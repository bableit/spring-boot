package org.springframework.boot.configurationprocessor.impaxee.schema;

import java.util.Map;
import java.util.function.Consumer;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;

import org.springframework.boot.configurationprocessor.impaxee.AnnotationUtils;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONException;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;

public abstract class FormItem
{
	public static final String KEY_KEY = "key";
	public static final String TOPIC_KEY = "topic";
	public static final String SECTION_KEY = "section";
	public static final String FIELDSET_KEY = "fieldset";
	public static final String JAVADOC_KEY = "javadoc";

	private String path;
	private String key;
	private String javaDoc;
	private String topic;
	private String section;
	private String fieldset;
		
	protected FormItem( String path, String key )
	{
		this( path, key, null );
	}
	
	protected FormItem( String path, String key, String javaDoc )
	{
		this.path = path;
		this.key = key;
		this.javaDoc = javaDoc;
	}
	
	public String getPath() 
	{
		return path;
	}
	
	public String getKey()
	{
		return key;
	}
		
	public String getJavaDoc() {
		return javaDoc;
	}
	
	public String getTopic() {
		return topic;
	}
	
	public void setTopic( String topic ) {
		this.topic = topic;
	}
	
	public String getSection() {
		return section;
	}
	
	public void setSection( String section ) {
		this.section = section;
	}
	
	public String getFieldset() {
		return fieldset;
	}
	
	public void setFieldset( String fieldset ) {
		this.fieldset = fieldset;
	}
	
	protected final String getFullPath()
	{
		StringBuilder s = new StringBuilder();
		if ( path != null )
		{
			s.append(path.trim());
		}
		if ( key != null )
		{
			if ( s.length()>0 )
			{
				s.append(".");
			}
			s.append(key.trim());
		}
		return s.toString();
	}
	
	protected void init( AnnotationMirror annotation )
	{
		Map<String,Object> properties = AnnotationUtils.getAnnotationValues(annotation);
		for( Map.Entry<String, Object> me : properties.entrySet() )
		{
			switch( me.getKey() )
			{
				case KEY_KEY: setIfValid( AnnotationUtils.getPropertyValue(
						properties, KEY_KEY, String.class ), value -> key=value );
					break;
				case JAVADOC_KEY: setIfValid( AnnotationUtils.getPropertyValue(
						properties, JAVADOC_KEY, String.class ), value -> javaDoc=value );
					break;
				case TOPIC_KEY: setIfValid( AnnotationUtils.getPropertyValue(
						properties, TOPIC_KEY, String.class ), value -> topic = value );
					break;
				case SECTION_KEY: setIfValid( AnnotationUtils.getPropertyValue(
						properties, SECTION_KEY, String.class ), value -> section = value );
					break;
				case FIELDSET_KEY: setIfValid( AnnotationUtils.getPropertyValue(
						properties, FIELDSET_KEY, String.class ), value -> fieldset = value );
					break;
			}
		}
	}
	
	protected final <T> void setIfValid( T value, Consumer<T> c )
	{
		if ( value != null )
		{
			Class<?> clazz = value.getClass();
			if ( String.class.isAssignableFrom( clazz ) && !String.class.cast(value).isEmpty() )
			{
				c.accept(value);
			}
			else if ( Boolean.class.isAssignableFrom( clazz ) && Boolean.class.cast(value) == true )
			{
				c.accept(value);
			}
			else if ( Integer.class.isAssignableFrom( clazz ) && Integer.class.cast(value) != -1 )
			{
				c.accept(value);
			}
			else if ( Number.class.isAssignableFrom( clazz ) && Number.class.cast(value).intValue() != -1 )
			{
				c.accept(value);
			}
		}
	}
	
	protected void putIfNonNull( JSONObject object, String key, Object value ) throws JSONException
	{
		if ( value != null )
		{
			object.put(key, value);
		}
	}
	
	public static String createKey( Element element )
	{
		if ( element instanceof VariableElement )
		{
			return element.getSimpleName().toString();
		}
		return null;
	}
	
	public abstract JSONObject toJSONSchema() throws JSONException;
	
	public abstract JSONObject toJSONLayout() throws JSONException;
}
