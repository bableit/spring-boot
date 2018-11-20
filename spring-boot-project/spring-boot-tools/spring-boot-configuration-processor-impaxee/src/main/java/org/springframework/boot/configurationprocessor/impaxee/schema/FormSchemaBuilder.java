package org.springframework.boot.configurationprocessor.impaxee.schema;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.configurationprocessor.impaxee.json.JSONException;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;

public class FormSchemaBuilder
{
	private final FormSchemaNode root = new FormSchemaNode(null);
		
	public JSONObject buildSchema() throws JSONException
	{
		return root.toJSON();
	}
	
	public FormSchemaBuilder addFormItem( FormItem item )
	{
		String path = item.getPath();
		addToTree( root, path==null ? null : 
			Arrays.stream( path.split("\\.") )
				.collect(Collectors.toList()), 
				new FormSchemaNode( item.getKey(), item ) );
		return this;
	}

	
	private static boolean addToTree( FormSchemaNode parent, List<String> parentPath, FormSchemaNode node)
	{
		if ( parentPath == null || parentPath.isEmpty() )
		{
			parent.addChildNode( node );
			return true;
		}
		else
		{
			final String key = parentPath.get(0);
			final List<String> newPath = parentPath.subList(1, parentPath.size() );
			
			for ( FormSchemaNode child : parent.getChildNodes() )
			{
				if ( child.getKey().equalsIgnoreCase( key ) )
				{
					if ( addToTree( child, newPath, node ) )
					{
						return true;
					}
				}
			}
			
			FormSchemaNode tmp = new FormSchemaNode( key );
			parent.addChildNode( tmp );
			
			return addToTree( tmp, newPath, node );
		}
	}
	
}
