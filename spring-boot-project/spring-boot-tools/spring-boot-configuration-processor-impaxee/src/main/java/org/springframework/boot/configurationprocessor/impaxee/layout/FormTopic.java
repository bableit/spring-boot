package org.springframework.boot.configurationprocessor.impaxee.layout;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.configurationprocessor.impaxee.json.JSONArray;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONException;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;

public class FormTopic 
{	
	private final FormSection defaultSection = new FormSection( "defaultSection" );
	
	private final String key;
	private Map<String, FormSection> sections;
	
	public FormTopic( String key )
	{
		this.key = key;
	}
	
	public String getKey()
	{
		return key;
	}

	public Map<String, FormSection> getFormSections()
	{
		return sections!=null ? Collections.unmodifiableMap(sections) :
			Collections.emptyMap();
	}
	
	public FormSection getOrAddSection( String sectionKey )
	{
		if ( sections == null )
		{
			sections = createSectionMap();
		}
		
		FormSection section = defaultSection;
		if ( sectionKey!=null && !sectionKey.isBlank() )
		{
			section = sections.get(sectionKey);
			if ( section == null )
			{
				section = new FormSection( sectionKey );
			}
		}
		
		if ( !sections.containsKey(section.getKey()) )
		{
			sections.put( section.getKey(), section );
		}
		
		return section;
	}
	
	public JSONObject toJSONLayout() throws JSONException
	{
		JSONObject object = createJSON();
		if ( sections != null && !sections.isEmpty() )
		{
			JSONArray array = new JSONArray();
			for ( FormSection section : sections.values() )
			{
				array.put( section.toJSONLayout() );
			}
			object.put("items", array);
		}
		return object;
	}
	
	private JSONObject createJSON() throws JSONException
	{
		JSONObject object = new JSONObject();
		object.put("type", "section");
		object.put("title", key );
		object.put("condition", "topicSelected=="+key );
		return object;
	}
	
	private Map<String, FormSection> createSectionMap()
	{
		return new TreeMap<>();
	}
}
