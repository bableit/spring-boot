package org.springframework.boot.configurationprocessor.impaxee.layout;

import java.util.Map;
import java.util.function.Consumer;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import org.springframework.boot.configurationprocessor.impaxee.AnnotationUtils;
import org.springframework.boot.configurationprocessor.impaxee.schema.FormItem;

public class Form 
{
	private static final String TOPIC_KEY = "topic";
	private static final String SECTION_KEY = "section";
	private static final String FIELDSET_KEY = "fieldset";

	private final Element formElement;
	private String topic;
	private String section;
	private String fieldset;

	private Form( Element formElement )
	{
		this.formElement = formElement;
	}
		
	public static Form create( Element element, String configPath )
	{
		Form form = getDefaultForm( element, configPath );
		
		AnnotationMirror annotation = AnnotationUtils.getAnnotation(element, AnnotationUtils.FORM_ANNOTATION );
		if ( annotation != null )
		{
			form.init(annotation);
		}
		
		return form;
	}	
	
	public Element getElement() {
		return formElement;
	}

	public String getTopic() {
		return topic;
	}

	public String getSection() {
		return section;
	}

	public String getFieldset() {
		return fieldset;
	}
	
	public void adjustFormItem( FormItem item ) {
		if ( item.getTopic()==null || item.getTopic().isEmpty() )
			item.setTopic( topic );
		if ( item.getSection()==null || item.getSection().isEmpty() )
			item.setSection( section );
		if ( item.getFieldset()==null || item.getFieldset().isEmpty() )
			item.setFieldset( fieldset );
	}
	
	private void init( AnnotationMirror annotation )
	{
		Map<String,Object> properties = AnnotationUtils.getAnnotationValues(annotation);
		properties.entrySet().forEach( entry -> {
			final String key = entry.getKey();
			switch( key )
			{
			case TOPIC_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, TOPIC_KEY, String.class), value -> topic=value );
			break;
			case SECTION_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, SECTION_KEY, String.class), value -> section=value );
			break;
			case FIELDSET_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, FIELDSET_KEY, String.class), value -> fieldset=value );
			break;
			}
		});
	}
	
	private static Form getDefaultForm( Element element, String configPath )
	{
		Form form = new Form( element );
		
		if ( configPath != null && configPath.length()>0 )
		{
			form.section = configPath;
		}
		
		return form;
	}
	
	private <T> void setIfValid( T value, Consumer<T> c )
	{
		if ( value != null )
		{
			if ( value instanceof String && ((String)value).isEmpty())
				return;
			c.accept(value);
		}
	}
}
