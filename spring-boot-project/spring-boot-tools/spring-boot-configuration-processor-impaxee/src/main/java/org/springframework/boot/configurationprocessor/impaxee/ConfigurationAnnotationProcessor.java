/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.configurationprocessor.impaxee;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.springframework.boot.configurationprocessor.impaxee.fieldvalues.FieldValuesParser;
import org.springframework.boot.configurationprocessor.impaxee.fieldvalues.javac.JavaCompilerFieldValuesParser;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONArray;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;
import org.springframework.boot.configurationprocessor.impaxee.layout.Form;
import org.springframework.boot.configurationprocessor.impaxee.layout.FormLayoutBuilder;
import org.springframework.boot.configurationprocessor.impaxee.schema.FormField;
import org.springframework.boot.configurationprocessor.impaxee.schema.FormFieldGroup;
import org.springframework.boot.configurationprocessor.impaxee.schema.FormFieldGroup.GroupType;
import org.springframework.boot.configurationprocessor.impaxee.schema.FormItem;
import org.springframework.boot.configurationprocessor.impaxee.schema.FormSchemaBuilder;

@SupportedAnnotationTypes({ AnnotationUtils.CONFIGURATION_PROPERTIES_ANNOTATION })
public class ConfigurationAnnotationProcessor extends AbstractProcessor
{

	private static final String NESTED_CONFIGURATION_PROPERTY_ANNOTATION = "org.springframework.boot."
			+ "context.properties.NestedConfigurationProperty";
	
	private static final String FORM_SCHEMA_PATH = "static/configeditor/configform-schema.json";
	
	private static final String FORM_LAYOUT_PATH = "static/configeditor/configform-layout.json";

	private static final String LOMBOK_DATA_ANNOTATION = "lombok.Data";

	private static final String LOMBOK_GETTER_ANNOTATION = "lombok.Getter";

	private static final String LOMBOK_SETTER_ANNOTATION = "lombok.Setter";

	private static final String LOMBOK_ACCESS_LEVEL_PUBLIC = "PUBLIC";

	private TypeUtils typeUtils;
	
	private Elements elementUtils;

	private FieldValuesParser fieldValuesParser;
	
	private FormSchemaBuilder schemaBuilder;
	
	private FormLayoutBuilder layoutBuilder;

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public synchronized void init(ProcessingEnvironment env) 
	{
		super.init(env);
		this.typeUtils = new TypeUtils(env);
		this.elementUtils = env.getElementUtils();
		this.schemaBuilder = new FormSchemaBuilder();
		this.layoutBuilder = new FormLayoutBuilder();
		
		try 
		{
			this.fieldValuesParser = new JavaCompilerFieldValuesParser(env);
		}
		catch (Throwable ex) 
		{
			this.fieldValuesParser = FieldValuesParser.NONE;
			logWarning("Field value processing of @ConfigrationProperties is not supported" );
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
	{
		TypeElement annotationType = elementUtils.getTypeElement( 
				AnnotationUtils.CONFIGURATION_PROPERTIES_ANNOTATION );

		if (annotationType != null) // Is @ConfigurationProperties available
		{ 
			for ( Element element : roundEnv.getElementsAnnotatedWith(annotationType) )
			{
				String configPath = getConfigPath( AnnotationUtils.getAnnotation( 
						element, AnnotationUtils.CONFIGURATION_PROPERTIES_ANNOTATION ) );
				
				Form form = Form.create( element, configPath );
				
				processElement( configPath, form, element, true );
			}
		}

		if ( roundEnv.processingOver() )
		{
			writeJSONSchema();
			writeJSONLayout();
		}
		
		return false;
	}
	
	private List<FormItem> processElement( String configPath, Form form, Element element, boolean addToBuilder ) 
	{
		try 
		{
			if (element instanceof TypeElement)
			{
				return processTypeElement(configPath, form, (TypeElement) element, null, addToBuilder );
			}
			else if (element instanceof ExecutableElement)
			{
				return processExecutableElement(configPath, form, (ExecutableElement) element, addToBuilder );
			}
		}
		catch (Exception ex)
		{
			throw new IllegalStateException( "Error processing configuration form on " + element, ex);
		}
		return Collections.emptyList();
	}
	
	private List<FormItem> processExecutableElement(String prefix, Form form, ExecutableElement element, boolean addToBuilder) 
	{
		if (element.getModifiers().contains(Modifier.PUBLIC)
				&& (TypeKind.VOID != element.getReturnType().getKind())) 
		{
			Element returns = this.processingEnv.getTypeUtils()
					.asElement(element.getReturnType());
			if (returns instanceof TypeElement)
			{
				return processTypeElement(prefix, form, (TypeElement) returns, element, addToBuilder );
			}
		}
		return Collections.emptyList();
	}

	private List<FormItem> processTypeElement(String configPath, Form form, TypeElement element, ExecutableElement source, boolean addToBuilder) 
	{
		List<FormItem> elements = null;
		
		if ( element.getKind() == ElementKind.ENUM )
		{
			return Collections.singletonList( FormField.create(element, typeUtils, configPath, null, getEnumValues(element)));
		}
		else 
		{
			FormField.Type type = FormField.Type.fromJavaType( typeUtils.getType( element.asType() ), null );
			
			if ( type != null )
			{
				return Collections.singletonList( FormField.create(element, typeUtils, configPath, null, getEnumValues(element) ) );
			}
			else 
			{
				TypeElementMembers members = new TypeElementMembers(this.processingEnv, this.fieldValuesParser, element);
				Map<String, Object> fieldValues = members.getFieldValues();

				elements = addToList( elements, 
						processSimpleTypes(configPath, form, element, source, members, fieldValues, addToBuilder ) );

				elements = addToList( elements, 
						processSimpleLombokTypes(configPath, form,  element, source, members, fieldValues, addToBuilder ) );

				elements = addToList( elements, 
						processNestedOrCollectionTypes(configPath, form, element, source, members, addToBuilder) );

				elements = addToList( elements, 
						processNestedOrCollectionLombokTypes(configPath, form, element, source, members, addToBuilder ) );

				return elements;
			}
		}
	}

	private List<FormItem> processSimpleTypes(String configPath, Form form, TypeElement element,
			ExecutableElement source, TypeElementMembers members,
			Map<String, Object> fieldValues, boolean addToBuilder) 
	{
		List<FormItem> items = null;
		for ( Map.Entry<String, VariableElement> me : members.getFields().entrySet() )
		{
			String name = me.getKey();
			VariableElement field = me.getValue();
			boolean hasSetter = members.getPublicSetter(name, field.asType()) != null;

			if ( hasSetter && !(isLombokField(field, element) && hasLombokSetter( field, element )))
			{
				FormItem item = processSimpleType( configPath, form, element, field, fieldValues.get(name) );
				if ( item != null )
				{
					items = addFormItem( form, item, items, addToBuilder );
				}
			}
		};
		return items;
	}
	
	private List<FormItem> processSimpleLombokTypes(String configPath, Form form, TypeElement element,
			ExecutableElement source, TypeElementMembers members,
			Map<String, Object> fieldValues, boolean addToBuilder )
	{
		List<FormItem> items = null;
		for ( Map.Entry<String, VariableElement> me : members.getFields().entrySet() )
		{
			VariableElement field = me.getValue();

			if ( isLombokField(field, element) && hasLombokSetter( field, element ) ) 
			{
				FormItem item = processSimpleType( configPath, form, element, field, fieldValues.get(me.getKey() ) );
				if ( item != null )
				{
					items = addFormItem( form, item, items, addToBuilder );
				}
			}
		};
		
		return items;
	}

	private List<FormItem> processNestedOrCollectionTypes(String configPath, Form form, TypeElement element,
			ExecutableElement source, TypeElementMembers members, boolean addToBuilder ) 
	{
		List<FormItem> items = null;
		for ( Map.Entry<String, VariableElement> me : members.getFields().entrySet() )
		{
			VariableElement field = me.getValue();
			if ( !isLombokField(field, element) )
			{
				ExecutableElement getter = members.getPublicGetter( me.getKey(), field.asType());
				
				FormFieldGroup group = processNestedOrCollectionType( configPath, form, element, source, getter, field);
				if ( group != null )
				{
					items = addFormItem( form, group, items, addToBuilder );
				}
			}
		};
		return items;
	}
		
	private List<FormItem> processNestedOrCollectionLombokTypes(String configPath, Form form, TypeElement element,
			ExecutableElement source, TypeElementMembers members, boolean addToBuilder ) 
	{
		List<FormItem> items = null;
		for ( Map.Entry<String, VariableElement> me : members.getFields().entrySet() )
		{
			VariableElement field = me.getValue();

			if ( isLombokField(field, element) ) 
			{
				ExecutableElement getter = members.getPublicGetter( me.getKey(), field.asType());
				
				FormFieldGroup group = processNestedOrCollectionType( configPath, form, element, source, getter, field );
				if ( group != null )
				{
					items = addFormItem( form, group, items, addToBuilder );
				}
			}
		};
		return items;
	}
	
	private FormField processSimpleType( String configPath, Form form, TypeElement element, VariableElement field, Object defaultValue )
	{
		if ( field != null && !isFinal( field ) )
		{
			TypeMirror returnType = field.asType();
			Element typeElement = this.processingEnv.getTypeUtils().asElement(returnType);
			boolean isNested = isNested(typeElement, field, element) && !FormField.isConvertable(typeElement);
			boolean isCollectionOrMap = this.typeUtils.isCollectionOrMap(returnType);
			boolean isArray = returnType.getKind() == TypeKind.ARRAY;
	
			if ( !isNested && !isCollectionOrMap && !isArray )
			{
				return FormField.create(field, typeUtils, configPath, defaultValue, getEnumValues( field ) );
			}
		}
		
		return null;
	}
	
	private FormFieldGroup processNestedOrCollectionType( String configPath, Form form, TypeElement element, 
			ExecutableElement source, ExecutableElement getter, VariableElement field )
	{
		AnnotationMirror annotation = AnnotationUtils.getAnnotation(getter, AnnotationUtils.CONFIGURATION_PROPERTIES_ANNOTATION);
		if ( annotation == null )
		{
			TypeMirror type = field.asType();
			Element typeElement = this.processingEnv.getTypeUtils().asElement(type);
			boolean isNested = isNested(typeElement, field, element) && !FormField.isConvertable(typeElement);
			boolean isCollection = this.typeUtils.isCollection(type);
			boolean isMap = this.typeUtils.isMap(type);
			boolean isArray = type.getKind() == TypeKind.ARRAY;
			
			if ( isMap )
			{
				logWarning( String.format( "Skipping configuration field ['%s' in %s]: Type declaration of field is java.lang.Map", 
						field, form.getElement() ) ); 
			}
			else if (typeElement instanceof TypeElement && ( isNested || isCollection || isArray ) ) 
			{
				FormFieldGroup group = FormFieldGroup.create( isNested ? GroupType.Nested : isCollection ? 
						GroupType.Collection : GroupType.Array,  field, typeUtils, configPath );
				
				String groupPath = configPath + "." + group.getKey();
				
				List<FormItem> subItems = Collections.emptyList();
				if ( isNested )
				{
					subItems = processTypeElement( groupPath , form, (TypeElement) typeElement, source, false );
				}
				else if ( type instanceof DeclaredType )
				{
					groupPath += "[]";

					if ( isCollection )
					{
						List<? extends TypeMirror> typeArgs = ((DeclaredType)type).getTypeArguments();
						if ( typeArgs == null || typeArgs.size() != 1 )
						{
							logWarning( String.format("Skipping configuration field ['%s' in %s]: Type declaration of field is a generic collection type, but without any type arguments.",
									field, form.getElement() ) );
						}
						else
						{
							subItems = processTypeElement( groupPath, form, asTypeElement(typeArgs.get(0)), source, false );
						}
					}
					else if ( isArray )
					{
						TypeMirror componentType = ((ArrayType)type).getComponentType();
						subItems = processTypeElement( groupPath, form, asTypeElement( componentType ), source, false );
					}
				}
				
				group.setItems(subItems);
				
				return group;
			}
		}
		
		return null;
	}
	
	private List<FormItem> addFormItem( Form form, FormItem item, List<FormItem> items, boolean addToBuilder )
	{
		if( item != null )
		{
			form.adjustFormItem(item);
			if ( addToBuilder )
			{
				schemaBuilder.addFormItem(item);
				layoutBuilder.addFormItem(item);
			}
			return addToList( items, Collections.singletonList( item ) );
		}
		return items;
	}
	
	private TypeElement asTypeElement( TypeMirror typeMirror )
	{
		return (TypeElement ) processingEnv.getTypeUtils().asElement( typeMirror );
	}
		
	private void logWarning(String msg) {
		log(Kind.WARNING, msg);
	}
	
	private void log(Kind kind, String msg) {
		this.processingEnv.getMessager().printMessage(kind, msg);
	}
	
	private void writeJSONSchema()
	{
		try
		{
			JSONObject schema = schemaBuilder.buildSchema();
			FileObject file = processingEnv.getFiler().createResource( StandardLocation.CLASS_OUTPUT, "", FORM_SCHEMA_PATH );
			
			try( OutputStream out = file.openOutputStream() )
			{
				out.write(schema.toString(2).getBytes(StandardCharsets.UTF_8));
			}
		}
		catch( Exception e )
		{
			throw new IllegalStateException( String.format( "Failed to write configform schema (%s)", FORM_SCHEMA_PATH ), e );
		}
	}
	
	private void writeJSONLayout()
	{
		try
		{
			JSONArray layout = layoutBuilder.buildLayout();
			FileObject file = processingEnv.getFiler().createResource( StandardLocation.CLASS_OUTPUT, "", FORM_LAYOUT_PATH );
			try( OutputStream out = file.openOutputStream() )
			{
				out.write(layout.toString(2).getBytes(StandardCharsets.UTF_8));
			}
		}
		catch( Exception e )
		{
			throw new IllegalStateException( String.format( "Failed to write configform layout (%s)", FORM_LAYOUT_PATH ), e );
		}
	}
	
	private static boolean isNested(Element returnType, VariableElement field, TypeElement element) 
	{
		if (AnnotationUtils.hasAnnotation(field, NESTED_CONFIGURATION_PROPERTY_ANNOTATION ) ) 
		{
			return true;
		}
		if (isCyclePresent(returnType, element)) 
		{
			return false;
		}
		return (isParentTheSame(returnType, element))
				&& returnType.getKind() != ElementKind.ENUM;
	}

	private static boolean isCyclePresent(Element returnType, Element element)
	{
		if (!(element.getEnclosingElement() instanceof TypeElement))
		{
			return false;
		}
		if (element.getEnclosingElement().equals(returnType)) 
		{
			return true;
		}
		return isCyclePresent(returnType, element.getEnclosingElement());
	}
	
    private static boolean isFinal(Element element) 
    {
        return element.getModifiers().contains(Modifier.FINAL);
    }

	private static boolean isParentTheSame(Element returnType, TypeElement element) 
	{
		if (returnType == null || element == null)
		{
			return false;
		}
		return getTopLevelType(returnType).equals(getTopLevelType(element));
	}

	private static Element getTopLevelType(Element element) 
	{
		if (!(element.getEnclosingElement() instanceof TypeElement)) 
		{
			return element;
		}
		return getTopLevelType(element.getEnclosingElement());
	}
	
	private static String getConfigPath( AnnotationMirror annotation )
	{
		Map<String, Object> elementValues = AnnotationUtils.getAnnotationValues(annotation);
		Object prefix = elementValues.get("prefix");
		if (prefix != null && !"".equals(prefix)) 
		{
			return (String) prefix;
		}
		Object value = elementValues.get("value");
		if (value != null && !"".equals(value)) 
		{
			return (String) value;
		}
		return null;
	}
	
	private static <T> List<T> addToList( List<T> list, Collection<T> toAdd )
	{
		if ( toAdd != null && !toAdd.isEmpty() )
		{
			if ( list == null )
			{
				list = new ArrayList<>(4);
			}
			list.addAll(toAdd);
		}
		return list;
	}
	
	private Object[] getEnumValues( Element element )
	{
		if ( element != null )
		{
			Element enumType = element instanceof TypeElement ? element :
					processingEnv.getTypeUtils().asElement( element.asType() );
			
			if ( enumType != null && enumType.getKind() == ElementKind.ENUM )
			{
				return enumType.getEnclosedElements().stream()
						.filter( e -> e.getKind() == ElementKind.ENUM_CONSTANT )
						.map( e -> e.getSimpleName() )
						.toArray();
			}
		}
		return null;
	}	

	private static boolean isLombokField(VariableElement field, TypeElement element)
	{
		return hasLombokPublicAccessor(field, element, true);
	}

	private static boolean hasLombokSetter(VariableElement field, TypeElement element)
	{
		return !field.getModifiers().contains(Modifier.FINAL)
				&& hasLombokPublicAccessor(field, element, false);
	}

	/**
	 * Determine if the specified {@link VariableElement field} defines a public accessor
	 * using lombok annotations.
	 * @param field the field to inspect
	 * @param element the parent element of the field (i.e. its holding class)
	 * @param getter {@code true} to look for the read accessor, {@code false} for the
	 * write accessor
	 * @return {@code true} if this field has a public accessor of the specified type
	 */
	private static boolean hasLombokPublicAccessor(VariableElement field, TypeElement element, boolean getter)
	{
		String annotation = (getter ? LOMBOK_GETTER_ANNOTATION : LOMBOK_SETTER_ANNOTATION);
		AnnotationMirror lombokMethodAnnotationOnField = AnnotationUtils.getAnnotation(field, annotation);
		if (lombokMethodAnnotationOnField != null) 
		{
			return isAccessLevelPublic(lombokMethodAnnotationOnField);
		}
		
		AnnotationMirror lombokMethodAnnotationOnElement = AnnotationUtils.getAnnotation(element, annotation);
		if (lombokMethodAnnotationOnElement != null) 
		{
			return isAccessLevelPublic(lombokMethodAnnotationOnElement);
		}
		
		return AnnotationUtils.hasAnnotation(element, LOMBOK_DATA_ANNOTATION);
	}

	private static boolean isAccessLevelPublic(AnnotationMirror lombokAnnotation) 
	{
		Map<String, Object> values = AnnotationUtils.getAnnotationValues(lombokAnnotation);
		Object value = values.get("value");
		return (value == null || value.toString().equals(LOMBOK_ACCESS_LEVEL_PUBLIC));
	}

}
