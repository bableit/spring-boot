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
	public static enum GroupType { Nested, Array, Collection }
	
	private static final String MIN_KEY = "min";
	private static final String MAX_KEY = "max";
	private static final String CONDITION_KEY = "condition";
	private static final String TITLE_KEY = "title";
	
	private final GroupType type;
	private String title;
	private Integer min;
	private Integer max;
	private String condition;
	private List<FormItem> items = new ArrayList<>();
	
	private FormFieldGroup( GroupType type, String path, String key, String javaDoc ) 
	{
		super( path, key, javaDoc );
		this.type = type;
	}
		
	public static FormFieldGroup create( GroupType type, Element element, TypeUtils typeUtils, String configPath )
	{
		FormFieldGroup object = getDefaultObject( type, element, typeUtils, configPath );
		
		AnnotationMirror annotation = AnnotationUtils.getAnnotation(element, AnnotationUtils.FORM_GROUP_ANNOTATION);
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
	
	public List<FormItem> getItems()
	{
		return Collections.unmodifiableList(items);
	}
	
	public void setItems( List<FormItem> items )
	{
		this.items.clear();
		if ( items != null )
		{
			for ( FormItem item : items )
			{
				item.setParentGroup(this);
				
				this.items.add(item);
			}
		}
	}
	
	@Override
	public JSONObject toJSONSchema() throws JSONException
	{
		JSONObject object = createJSON();
		
		if ( items != null )
		{
			if ( isNested() )
			{
				JSONObject properties = new JSONObject();
				for ( FormItem item : items )
				{
					properties.put( item.getKey(), item.toJSONSchema() );
				}
				object.put( "properties", properties );
			}
			else
			{
				if ( items.size() == 1 )
				{
					object.put( "items", items.get(0).toJSONSchema() );
				}
				else
				{
					JSONObject itemObject = new JSONObject();
					itemObject.put("type", "object" );
					
					for ( FormItem item : items )
					{
						itemObject.put( item.getKey(), item.toJSONSchema() );
					}
					
					object.put( "items", itemObject );
				}
			}
		}
		
		return object;
	}
	
	@Override
	public JSONObject toJSONLayout() throws JSONException
	{
		JSONObject object = new JSONObject();
		putIfNonNull( object, "type", isNested() ? "section" : "array" );
		putIfNonNull( object, "condition", condition );
		
		JSONArray array = new JSONArray();
		if ( items != null )
		{
			if ( items.size() == 1 && (isArray() || isCollection() ) )
			{
				FormItem item = items.get(0);
				JSONObject itemJSON = item.toJSONLayout();
				putIfNonNull( itemJSON, "key", getPath() );
				array.put(itemJSON);
			}
			else
			{
				for ( FormItem item : items )
				{
					JSONObject itemJSON = item.toJSONLayout();
					putIfNonNull( itemJSON, "key", item.getFullPath() );
					array.put( itemJSON );
				}
			}
		}
		
		object.put("items", array);
		return object;
	}
	
	private JSONObject createJSON() throws JSONException
	{
		JSONObject object = new JSONObject();
		object.put("type", isNested() ? "object" : "array" );
		putIfNonNull( object, "title", title );
		putIfNonNull( object, "description", getJavaDoc() );

		if ( isArray() || isCollection() )
		{
			if ( min != null )
				object.put("minItems", min );
			if ( max != null )
				object.put("maxItems", max );
		}
		
		return object;
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
				case TITLE_KEY: setIfValid( AnnotationUtils.getPropertyValue(
						properties, TITLE_KEY, String.class ), value -> title=value );
					break;
				}
			}
		}
	}
	
	private static String createKey( Element element )
	{
		return element.getSimpleName().toString();
	}
	
	private static FormFieldGroup getDefaultObject( GroupType type, Element element, TypeUtils typeUtils, String configPath )
	{
		FormFieldGroup group = new FormFieldGroup(type, configPath, createKey(element), typeUtils.getJavaDoc(element));
		
		// use capitalized key as default title
		String key = group.getKey();
		if ( key != null )
		{
			group.title = Character.toUpperCase(key.charAt(0)) + key.substring(1);
		}

		return group;
	}

}
