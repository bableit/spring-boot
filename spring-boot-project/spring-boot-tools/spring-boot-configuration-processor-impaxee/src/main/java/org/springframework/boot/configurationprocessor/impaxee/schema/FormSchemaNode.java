package org.springframework.boot.configurationprocessor.impaxee.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.configurationprocessor.impaxee.json.JSONException;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;

public class FormSchemaNode 
{
	private final String key;
	private final FormItem item;
	
	private FormSchemaNode parent;
	private List<FormSchemaNode> children;
	
	public FormSchemaNode( String key )
	{
		this( key, null );
	}
	
	public FormSchemaNode( String key, FormItem element )
	{
		this.key = key;
		this.item = element;
	}
	
	public FormSchemaNode getParent()
	{
		return parent;
	}
	
	public String getKey()
	{
		return key;
	}
	
	public boolean hasChildren()
	{
		return children!=null && !children.isEmpty();
	}

	public boolean isRoot()
	{
		return parent==null && (key==null || key.isEmpty());
	}
	
	public Optional<FormItem> getFormItem()
	{
		return Optional.ofNullable( item );
	}
	
	public List<FormSchemaNode> getChildNodes()
	{
		return children != null ? Collections.unmodifiableList(children) :
			Collections.emptyList();
	}
	
	public FormSchemaNode addChildNode( FormSchemaNode node )
	{
		if ( children == null )
		{
			children = new ArrayList<>(8);
		}
		children.add(node);
		node.parent = this;
		return this;
	}
	
	public JSONObject toJSON() throws JSONException
	{
		JSONObject object = createJSON();
		
		if ( hasChildren() )
		{
			JSONObject properties = new JSONObject();
			for( FormSchemaNode node : children )
			{
				String key = node.getKey();
				if ( key != null )
				{
					properties.put( node.getKey(), node.toJSON() );
				}
			}
			
			object.put( "properties", properties );
		}
		
		return object;
	}
	
	private JSONObject createJSON() throws JSONException
	{
		if ( item != null )
		{
			return item.toJSONSchema();
		}
		else
		{
			JSONObject object = new JSONObject();
			object.put( "type", "object" );
			
			if ( key != null && !key.isEmpty())
			{
				object.put("title", Character.toUpperCase(key.charAt(0)) + key.substring(1));
			}
			
			return object;
		}
	}
}
