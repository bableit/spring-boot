package org.springframework.boot.configurationprocessor.impaxee;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;

public class AnnotationUtils
{
	public static final String CONFIGURATION_PROPERTIES_ANNOTATION = "org.springframework.boot.context.properties.ConfigurationProperties";
	public static final String FORM_ANNOTATION = "com.agfa.ee.server.config.Form";
	public static final String FORM_INPUT_ANNOTATION = "com.agfa.ee.server.config.FormInput";
	public static final String FORM_NESTED_OBJECT_ANNOTATION = "com.agfa.ee.server.config.FormNestedObject";
	public static final String FORM_COLLECTION_ANNOTATION = "com.agfa.ee.server.config.FormCollection";
	
	public static boolean hasAnnotation(Element element, String type)
	{
		return getAnnotation(element, type) != null;
	}
	
	public static AnnotationMirror getAnnotation(Element element, String type)
	{
		if (element != null)
		{
			for (AnnotationMirror annotation : element.getAnnotationMirrors())
			{
				if (type.equals(annotation.getAnnotationType().toString()))
				{
					return annotation;
				}
			}
		}
		return null;
	}
	
	public static Map<String, Object> getAnnotationValues(AnnotationMirror annotation)
	{
		Map<String, Object> values = new LinkedHashMap<>();
		annotation.getElementValues().forEach((name, value) -> values
				.put(name.getSimpleName().toString(), value.getValue()));
		return values;
	}
	
	public static <T> T getPropertyValue( Map<String, Object> properties, String propertyKey, Class<T> propertyValueClass )
			throws ClassCastException
	{
		return getPropertyValue( properties, propertyKey, propertyValueClass, null );
	}
	
	public static <T> T getPropertyValue( Map<String, Object> properties, String propertyKey, Class<T> propertyValueClass, T defaultValue )
		throws ClassCastException
	{
		if ( properties != null )
		{
			return nullSafeCast( properties.getOrDefault(propertyKey, defaultValue), propertyValueClass );
		}
		return null;
	}
	
	public static <T> T nullSafeCast( Object o, Class<T> clazz ) throws ClassCastException
	{
		if ( o != null )
		{
			return clazz.cast(o);
		}
		return null;
	}
	
	public static Map<Element, List<Element>> getElementsAnnotatedOrMetaAnnotatedWith(
			RoundEnvironment roundEnv, TypeElement annotation, Elements elementUtils ) 
	{
		DeclaredType annotationType = (DeclaredType) annotation.asType();
		Map<Element, List<Element>> result = new LinkedHashMap<>();
		for (Element element : roundEnv.getRootElements()) {
			LinkedList<Element> stack = new LinkedList<>();
			stack.push(element);
			collectElementsAnnotatedOrMetaAnnotatedWith(annotationType, stack, elementUtils );
			stack.removeFirst();
			if (!stack.isEmpty()) {
				result.put(element, Collections.unmodifiableList(stack));
			}
		}
		return result;
	}

	private static boolean collectElementsAnnotatedOrMetaAnnotatedWith(
			DeclaredType annotationType, LinkedList<Element> stack, Elements elementUtils)
	{
		Element element = stack.peekLast();
		for (AnnotationMirror annotation : elementUtils.getAllAnnotationMirrors(element)) 
		{
			Element annotationElement = annotation.getAnnotationType().asElement();
			if (!stack.contains(annotationElement)) 
			{
				stack.addLast(annotationElement);
				if (annotationElement.equals(annotationType.asElement())) {
					return true;
				}
				if (!collectElementsAnnotatedOrMetaAnnotatedWith(annotationType, stack, elementUtils)) {
					stack.removeLast();
				}
			}
		}
		return false;
	}
}
