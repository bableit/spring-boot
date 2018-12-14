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
import javax.annotation.processing.SupportedOptions;
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
import javax.lang.model.type.PrimitiveType;
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

@SupportedOptions( {"org.springframework.boot.configurationprocessor.additionalMetadataLocations"} ) // just to avoid warning when processing
@SupportedAnnotationTypes({ AnnotationUtils.CONFIGURATION_PROPERTIES_ANNOTATION })
public class ConfigurationAnnotationProcessor extends AbstractProcessor
{

	private static final String NESTED_CONFIGURATION_PROPERTY_ANNOTATION = "org.springframework.boot."
			+ "context.properties.NestedConfigurationProperty";
		
	private static final String CONFIG_SCHEMA_PATH = "static/configeditor/config-schema.json";
	
	private static final String CONFIG_LAYOUT_PATH = "static/configeditor/config-layout.json";

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
			writeConfigSchema();
			writeConfigLayout();
		}
		
		return false;
	}
	
	private List<FormItem> processElement( String configPath, Form form, Element element, boolean addToBuilder ) 
	{
		try 
		{
			if (element instanceof TypeElement)
			{
				return processFields(configPath, form, (TypeElement) element, addToBuilder );
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
				return processFields(prefix, form, (TypeElement) returns, addToBuilder );
			}
		}
		return Collections.emptyList();
	}
	
	private List<FormItem> processFields( String configPath, Form form, TypeElement parent, boolean addToBuilder )
	{
		TypeElementMembers members = new TypeElementMembers(this.processingEnv, this.fieldValuesParser, parent);
		Map<String, Object> fieldValues = members.getFieldValues();
		
		List<FormItem> items = null;
		for ( Map.Entry<String, VariableElement> me : members.getFields().entrySet() )
		{
			String name = me.getKey();
			VariableElement field = me.getValue();
			
			if ( !isFinal( field ) && !isStatic(field) )
			{
				ExecutableElement getter = members.getPublicGetter( name, field.asType());
				ExecutableElement setter = getSetter( members, field );
				boolean hasConfigPropAnnotation = AnnotationUtils.getAnnotation(getter, 
						AnnotationUtils.CONFIGURATION_PROPERTIES_ANNOTATION) != null;
				boolean hasNoFormFieldAnnotation = AnnotationUtils.getAnnotation(field, 
						AnnotationUtils.NO_FORM_FIELD_ANNOTATION) != null;
				
				if ( !hasConfigPropAnnotation && !hasNoFormFieldAnnotation &&
						( setter!=null || hasLombokSetter( field, parent ) ) )
				{
					TypeMirror type = getEffectiveType( field, setter );
					Element typeElement = this.processingEnv.getTypeUtils().asElement(type);
					
					boolean isDeclared = type.getKind()==TypeKind.DECLARED && 
							!isCyclePresent(typeElement, parent ) && !FormField.isConvertable(typeElement);
					boolean isCollection = this.typeUtils.isCollection(type);
					boolean isMap = this.typeUtils.isMap(type);
					boolean isArray = type.getKind() == TypeKind.ARRAY;
					boolean isSimpleType = FormField.Type.fromJavaType( typeUtils.getType( type ), null )!=null ||
							FormField.isConvertable(typeElement);
					
					if ( isMap ) //java.lang.Map is not supported
					{
						logWarning( String.format( "Skipping configuration field ['%s' in %s]: Type declaration of field is java.lang.Map", 
								field, form.getElement() ) ); 
						continue;
					}
					else if ( isArray ) // array
					{
						items = addFormItem( form,
								processArray( configPath, form, field, type ),
								items, addToBuilder );
					}
					else if ( isCollection ) // collection
					{
						items = addFormItem( form,
								processCollection( configPath, form, field, type ),
								items, addToBuilder );
					}
					else if ( isSimpleType ) // a simple type
					{
						items = addFormItem( form, 
								FormField.create(field, type, typeUtils, configPath, fieldValues.get(name), getEnumValues( field ) ), 
								items, addToBuilder ) ;
					}
					else if ( isDeclared ) // nested object
					{
						items = addFormItem( form, 
								processDeclaredObject( configPath, form, field, type ), 
								items, addToBuilder );
					}
					else
					{
						logWarning( String.format( "Skipping configuration field ['%s' in %s]: Type '%s' is not supported", 
								name, form.getElement(), type ) ); 
					}
				}
			}
		};
		return items;
	}
	
	
	private List<FormItem> processTypeArgElement(String configPath, Form form, TypeElement element) 
	{
		TypeMirror type = element.asType();

		boolean isCollection = this.typeUtils.isCollection(type);
		boolean isMap = this.typeUtils.isMap(type);
		boolean isArray = type.getKind() == TypeKind.ARRAY;
		boolean isEnum = element.getKind() == ElementKind.ENUM;
		boolean isDeclared = type.getKind() == TypeKind.DECLARED;
		boolean isFieldType = FormField.Type.fromJavaType( typeUtils.getType( element.asType() ), null )!=null;
		
		if ( isMap ) //java.lang.Map is not supported
		{
			logWarning( String.format( "Skipping configuration field ['%s' in %s]: Type declaration of field is java.lang.Map", 
					element, form.getElement() ) ); 
		}
		else if ( isArray ) // array
		{
			return addFormItem( form,
					processArray( configPath, form, element, type ),
					null, false );
		}
		else if ( isCollection ) // collection
		{
			return addFormItem( form,
					processCollection( configPath, form, element, type ),
					null, false );
		}
		else if ( isEnum ) // enum
		{
			return addFormItem( form, 
					FormField.create(element, element.asType(), typeUtils, configPath, null, getEnumValues(element)),
					null, false );
		}
		else if ( isFieldType )
		{
			return addFormItem( form, 
					FormField.create( element, type, typeUtils, configPath, null, getEnumValues( element ) ), 
					null, false ) ;
		}
		else if ( isDeclared )
		{
			return addFormItem( form,
					processTypeArgObject( configPath, form, element, type ),
					null, false );
		}
		else
		{
			logWarning( String.format( "Skipping type argument of configuration field ['%s' in %s]: Type '%s' is not supported", 
					element, form.getElement(), type ) ); 
		}
		
		return Collections.emptyList();
	}
	
	private FormFieldGroup processTypeArgObject( String configPath, Form form, Element element, TypeMirror type )
	{
		FormFieldGroup group = FormFieldGroup.create( GroupType.Nested,  element, typeUtils, configPath );
		group.setItems( processFields( configPath , form, asTypeElement( type ), false ) );
		return group;
	}
	
	private FormFieldGroup processDeclaredObject( String configPath, Form form, Element element, TypeMirror type )
	{
		FormFieldGroup group = FormFieldGroup.create( GroupType.Nested,  element, typeUtils, configPath );
		group.setItems( processFields( configPath + "." + group.getKey() , form, asTypeElement( type ), false ) );
		return group;
	}
	
	private FormFieldGroup processArray( String configPath, Form form, Element element, TypeMirror type )
	{
		if ( type instanceof ArrayType )
		{
			TypeElement componentType = asTypeElement( ((ArrayType)type).getComponentType() );
			if ( componentType == null )
			{
				logWarning( String.format("Skipping configuration field ['%s' in %s]: The field declared an array with an unsupported component type",
						element, element.getEnclosingElement() ) );
			}
			else
			{
				FormFieldGroup group = FormFieldGroup.create( GroupType.Array,  element, typeUtils, configPath );
				group.setItems( processTypeArgElement( configPath + "." + group.getKey() + "[]", 
						form, componentType ) );
				return group;
			}
		}
		return null;
	}

	private FormFieldGroup processCollection( String configPath, Form form, Element element, TypeMirror type )
	{
		if ( type instanceof DeclaredType )
		{
			List<? extends TypeMirror> typeArgs = ((DeclaredType)type).getTypeArguments();
			if ( typeArgs == null || typeArgs.size() != 1 )
			{
				logWarning( String.format("Skipping configuration field ['%s' in %s]: Type declaration of field is a generic collection type, but with no or multiple type arguments.",
						element, form.getElement() ) );
			}
			else
			{
				TypeElement argType = asTypeElement( typeArgs.get(0) );
				if ( argType == null )
				{
					logWarning( String.format("Skipping configuration field ['%s' in %s]: The field declared a collection with an argument type that is not supported",
							element, element.getEnclosingElement() ) );
				}
				else
				{
					FormFieldGroup group = FormFieldGroup.create( GroupType.Collection,  element, typeUtils, configPath );
					group.setItems( processTypeArgElement( configPath + "." + group.getKey() + "[]", form, argType ) );
					return group;
				}
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
		if ( typeMirror instanceof PrimitiveType )
		{
			return processingEnv.getTypeUtils().boxedClass((PrimitiveType)typeMirror);
		}
		
		return (TypeElement) processingEnv.getTypeUtils().asElement( typeMirror );
	}
		
	private void logWarning(String msg) {
		log(Kind.WARNING, msg);
	}
	
	private void log(Kind kind, String msg) {
		this.processingEnv.getMessager().printMessage(kind, msg);
	}
	
	private void writeConfigSchema()
	{
		try
		{
			JSONObject schema = schemaBuilder.buildSchema();
			FileObject file = processingEnv.getFiler().createResource( StandardLocation.CLASS_OUTPUT, "", CONFIG_SCHEMA_PATH );
			
			try( OutputStream out = file.openOutputStream() )
			{
				out.write(schema.toString(2).getBytes(StandardCharsets.UTF_8));
			}
		}
		catch( Exception e )
		{
			throw new IllegalStateException( String.format( "Failed to write config schema (%s)", CONFIG_SCHEMA_PATH ), e );
		}
	}
	
	private void writeConfigLayout()
	{
		try
		{
			JSONArray layout = layoutBuilder.buildLayout();
			FileObject file = processingEnv.getFiler().createResource( StandardLocation.CLASS_OUTPUT, "", CONFIG_LAYOUT_PATH );
			try( OutputStream out = file.openOutputStream() )
			{
				out.write(layout.toString(2).getBytes(StandardCharsets.UTF_8));
			}
			
		}
		catch( Exception e )
		{
			throw new IllegalStateException( String.format( "Failed to write config layout (%s)", CONFIG_LAYOUT_PATH ), e );
		}
	}

	@SuppressWarnings("unused")
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
    
    private static boolean isStatic( Element element )
    {
    	return element.getModifiers().contains(Modifier.STATIC);
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
	
	private ExecutableElement getSetter( TypeElementMembers members, VariableElement field )
	{
		String name = field.getSimpleName().toString();
		List<ExecutableElement> candidates = members.getPublicSetters(name, 1);
		if ( !candidates.isEmpty() )
		{
			TypeMirror fieldType = field.asType();

			if ( candidates.size() > 1 )
			{
				// in the case of an array field type and there are multiple setter candidates,
				// we prefer the setter with the java.lang.String parameter
				if ( fieldType.getKind() == TypeKind.ARRAY )
				{
					ExecutableElement setter = members.getPublicSetterForParameter(candidates, 
							processingEnv.getElementUtils().getTypeElement("java.lang.String").asType() );
					if ( setter != null )
					{
						return setter;
					}
				}
				
				return members.getPublicSetterForParameter(candidates, fieldType );
			}
			else
			{
				return candidates.get(0);
			}
		}
		return null;
	}
	
	private TypeMirror getEffectiveType( VariableElement field, ExecutableElement setter )
	{
		TypeMirror type = field.asType();
		
		if ( setter != null )
		{
			List<? extends VariableElement> params = setter.getParameters();
			if( params != null && params.size() == 1 )
			{
				type = params.get(0).asType();
			}
		}
		
		// for maps we strictly use a java.lang.String data type
		if ( this.typeUtils.isMap(type) )
		{
			type = processingEnv.getElementUtils().getTypeElement("java.lang.String").asType();
		}
		
		return type;
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
