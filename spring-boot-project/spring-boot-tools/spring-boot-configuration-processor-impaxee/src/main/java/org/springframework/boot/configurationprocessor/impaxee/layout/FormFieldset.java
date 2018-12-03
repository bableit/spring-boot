package org.springframework.boot.configurationprocessor.impaxee.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.configurationprocessor.impaxee.json.JSONArray;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONException;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;
import org.springframework.boot.configurationprocessor.impaxee.schema.FormItem;

public class FormFieldset 
{	
	private final String key;
	private List<FormItem> items;
	
	public FormFieldset( String key )
	{
		this.key = key;
	}
	
	public String getKey()
	{
		return key;
	}

	public List<FormItem> getFormItems()
	{
		return items!=null ? Collections.unmodifiableList(items) :
			Collections.emptyList();
	}
	
	public void addFormItem( FormItem item )
	{
		if ( items == null )
		{
			items = new ArrayList<>();
		}
		items.add(item);
	}
	
	public JSONObject toJSONLayout() throws JSONException
	{
		JSONObject object = createJSON();
		if ( items != null )
		{
			JSONArray array = new JSONArray();
			for ( FormItem item : items )
			{
				array.put( item.toJSONLayout() );
			}
			object.put("items", array);
		}
		return object;
	}
	
	private JSONObject createJSON() throws JSONException
	{
		JSONObject object = new JSONObject();
		object.put("type", "fieldset" );
		if ( key != null && !key.isEmpty() )
		{
			object.put("expandable", true);
			object.put("title", key.substring( key.lastIndexOf(".") + 1 ) );
		}
		return object;
	}	
	
}
