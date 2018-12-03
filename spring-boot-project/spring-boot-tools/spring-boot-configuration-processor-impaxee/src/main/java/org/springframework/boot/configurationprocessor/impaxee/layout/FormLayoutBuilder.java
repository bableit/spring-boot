package org.springframework.boot.configurationprocessor.impaxee.layout;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.configurationprocessor.impaxee.json.JSONArray;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONException;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;
import org.springframework.boot.configurationprocessor.impaxee.schema.FormItem;

public class FormLayoutBuilder 
{
	private final FormTopic defaultTopic = new FormTopic("");
	
	private Map<String, FormTopic> topics;
			
	public void addFormItem( FormItem item )
	{
		FormTopic topic = getOrAddTopic( item.getTopic() );
		FormSection section = topic.getOrAddSection( item.getSection() );
		FormFieldset fieldset = section.getOrAddFieldset( item.getFieldset() );
		fieldset.addFormItem(item);
	}
	
	public JSONArray buildLayout() throws JSONException
	{
		JSONArray array = new JSONArray();
		
		// button actions
		array.put( createActionsJSON() );
		
		// topics
		for ( FormTopic topic : topics.values() )
		{
			array.put( topic.toJSONLayout() );
		}
				
		return array;
	}
	
	private FormTopic getOrAddTopic( String topicKey )
	{
		if ( topics == null )
		{
			topics = createTopicsMap();
		}
		
		FormTopic topic = defaultTopic;
		if ( topicKey != null && !topicKey.isEmpty() )
		{
			topic = topics.get( topicKey );
			if ( topic == null )
			{
				topic = new FormTopic( topicKey );
			}
		}
		
		if ( !topics.containsKey(topic.getKey()))
		{
			topics.put(topic.getKey(), topic);
		}
		
		return topic;
	}
	
	private static Map<String, FormTopic> createTopicsMap()
	{
		return new TreeMap<>();
	}
	
	private static JSONObject createActionsJSON() throws JSONException
	{
		JSONObject headingItem = new JSONObject();
		headingItem.put("type", "label");
		headingItem.put("htmlClass", "headerLabel h1");
		
		JSONObject saveItem = new JSONObject();
		saveItem.put("type", "submit");
		saveItem.put("title", "Save");
		saveItem.put("htmlClass", "pull-right");
		
		JSONArray actionItems = new JSONArray();
		actionItems.put(headingItem);
		actionItems.put(saveItem);
		
		JSONObject actions = new JSONObject();
		actions.put("type", "actions");
		actions.put("items",actionItems);
		actions.put("htmlClass", "header clearfix");
		
		return actions;
	}
}
