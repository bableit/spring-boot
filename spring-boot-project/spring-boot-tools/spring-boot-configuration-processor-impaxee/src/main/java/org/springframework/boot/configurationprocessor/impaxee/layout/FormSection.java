package org.springframework.boot.configurationprocessor.impaxee.layout;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.configurationprocessor.impaxee.json.JSONArray;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONException;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;

public class FormSection 
{	
	private final FormFieldset defaultFieldset = new FormFieldset("");
	
	private final String key;
	private Map<String, FormFieldset> fieldsets;
	
	public FormSection( String key )
	{
		this.key = key;
	}
	
	public String getKey()
	{
		return key;
	}

	public Map<String,FormFieldset> getFormFieldsets()
	{
		return fieldsets!=null ? Collections.unmodifiableMap(fieldsets) :
			Collections.emptyMap();
	}
	
	public FormFieldset getOrAddFieldset( String fieldsetKey )
	{
		if ( fieldsets == null )
		{
			fieldsets = createFieldsetMap();
		}
		
		FormFieldset fieldset = defaultFieldset;
		if ( fieldsetKey!=null && !fieldsetKey.isEmpty() )
		{
			fieldset = fieldsets.get(fieldsetKey);
			if ( fieldset == null )
			{
				fieldset = new FormFieldset( fieldsetKey );
			}
		}
		
		if ( !fieldsets.containsKey(fieldset.getKey()) )
		{
			fieldsets.put( fieldset.getKey(), fieldset );
		}
		
		return fieldset;
	}
	
	public JSONObject toJSONLayout() throws JSONException
	{
		JSONObject object = createJSON();
		if ( fieldsets != null )
		{
			JSONArray array = new JSONArray();
			for ( FormFieldset fieldset : fieldsets.values() )
			{
				array.put( fieldset.toJSONLayout() );
			}
			object.put("items", array);
		}
		return object;
	}
	
	private JSONObject createJSON() throws JSONException
	{
		JSONObject object = new JSONObject();
		object.put("type", fieldsets != null && fieldsets.size() > 1 ?
				"tabarray" :"section");
		if ( key != null && !key.isEmpty() )
		{
			object.put("title", key );
		}
		return object;
	}	
		
	private static Map<String, FormFieldset> createFieldsetMap()
	{
		return new TreeMap<>();
	}
	
}
