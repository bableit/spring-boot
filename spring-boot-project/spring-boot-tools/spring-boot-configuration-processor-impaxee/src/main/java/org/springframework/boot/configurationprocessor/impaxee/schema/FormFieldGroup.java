package org.springframework.boot.configurationprocessor.impaxee.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import org.springframework.boot.configurationprocessor.impaxee.AnnotationUtils;
import org.springframework.boot.configurationprocessor.impaxee.TypeUtils;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONArray;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONException;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;

public class FormFieldGroup extends FormItem 
{
	public static enum GroupType { Nested, Array, Collection, Map }
	
	public static final String MIN_KEY = "min";
	public static final String MAX_KEY = "max";
	public static final String CONDITION_KEY = "condition";
	
	private final GroupType type;
	private Integer min;
	private Integer max;
	private String condition;
	private List<FormItem> items = new ArrayList<>();
	
	private FormFieldGroup( GroupType type, String path, String key, String javaDoc ) 
	{
		super( path, key, javaDoc );
		this.type = type;
	}
		
	public static FormFieldGroup create( GroupType type, Element element, List<FormItem> childElements, TypeUtils typeUtils, String configPath )
	{
		FormFieldGroup object = getDefaultObject( type, element, childElements, typeUtils, configPath );
		
		AnnotationMirror annotation = AnnotationUtils.getAnnotation(element, AnnotationUtils.FORM_NESTED_OBJECT_ANNOTATION);
		if ( annotation != null )
		{
			object.init(annotation);
		}
		
		return object;
	}
		
	public boolean isNested()
	{
		return type == GroupType.Nested;
	}
	
	public boolean isArray()
	{
		return type == GroupType.Array;
	}
	
	public boolean isCollection()
	{
		return type == GroupType.Collection;
	}
	
	public boolean isMap()
	{
		return type == GroupType.Map;
	}
	
	public List<FormItem> getItems()
	{
		return Collections.unmodifiableList(items);
	}
	
	@Override
	public JSONObject toJSONSchema() throws JSONException
	{
		JSONObject object = createJSON();
		
		if ( items != null )
		{
			JSONObject itemsArray = new JSONObject();
			
			// Maps are not supported by any json-form out-of-the-box editor.
			// As a consequence, we need to mimic map support by just 'injecting' the
			// map key just as an ordinary additional 'input' field.
			// Of course, that key must again be removed while the
			// original map structure within the config is being recovered.
			if ( isMap() )
			{
				//TODO: mapkey
				String mapKey = "";
				itemsArray.put( mapKey, createJSON_MapKey( mapKey ) );
			}
			
			for ( FormItem item : items )
			{
				itemsArray.put( item.getKey(), item.toJSONSchema() );
			}

			object.put( isNested() ? "properties" : "items", itemsArray );
		}
		
		return object;
	}
	
	@Override
	public JSONObject toJSONLayout() throws JSONException
	{
		JSONObject object = new JSONObject();
		putIfNonNull( object, "title", getKey() );
		putIfNonNull( object, "type", isNested() ? "section" : "array" );
		putIfNonNull( object, "condition", condition );
		
		JSONArray array = new JSONArray();
		if ( items != null )
		{
			for ( FormItem item : items )
			{
				array.put( item.toJSONLayout() );
			}
		}
		
		object.put("items", array);
		return object;
	}
	
	private JSONObject createJSON() throws JSONException
	{
		JSONObject object = new JSONObject();
		object.put("type", isNested() ? "object" : "array" );
		object.put("title", getKey() );
		//TODO: handle javadoc
				
		if ( isArray() || isCollection() || isMap() )
		{
			if ( min != null )
				object.put("minItems", min );
			if ( max != null )
				object.put("maxItems", max );
		}
		
		return object;
	}
	
	private JSONObject createJSON_MapKey( String mapKey ) throws JSONException
	{
		JSONObject object = new JSONObject();
		object.put("type", "string"); // just 'string' keys are supported yet
		object.put("title", mapKey);
		object.put("enum", null); //TODO: enums
		return object;
	}
	
	private FormFieldGroup addItems( List<FormItem> childElements )
	{
		this.items.addAll(childElements);
		return this;
	}
	
	@Override
	protected void init( AnnotationMirror annotation )
	{
		super.init( annotation );
		
		if ( isArray() || isCollection() )
		{
			Map<String,Object> properties = AnnotationUtils.getAnnotationValues(annotation);
			for( Map.Entry<String, Object> me : properties.entrySet() )
			{
				final String key = me.getKey();
				switch( key )
				{
				case MIN_KEY: setIfValid( AnnotationUtils.getPropertyValue(
								properties, MIN_KEY, Integer.class ), value -> min=value );
					break;
				case MAX_KEY: setIfValid( AnnotationUtils.getPropertyValue(
								properties, MAX_KEY, Integer.class ), value -> max=value );
					break;
				case CONDITION_KEY: setIfValid( AnnotationUtils.getPropertyValue(
						properties, CONDITION_KEY, String.class ), value -> condition=value );
					break;
				}
			}
		}
	}
	
	private static FormFieldGroup getDefaultObject( GroupType type, Element element, List<FormItem> items, TypeUtils typeUtils, String configPath )
	{
		FormFieldGroup o = new FormFieldGroup(type, configPath, createKey(element), typeUtils.getJavaDoc(element));
		if ( items != null )
		{
			o.addItems(items);
		}
		return o;
	}

}
