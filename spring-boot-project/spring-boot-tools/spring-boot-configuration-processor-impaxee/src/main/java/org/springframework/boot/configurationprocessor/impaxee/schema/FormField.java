package org.springframework.boot.configurationprocessor.impaxee.schema;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import org.springframework.boot.configurationprocessor.impaxee.AnnotationUtils;
import org.springframework.boot.configurationprocessor.impaxee.TypeUtils;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONArray;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONException;
import org.springframework.boot.configurationprocessor.impaxee.json.JSONObject;

public class FormField extends FormItem
{
	private static final String TYPE_KEY = "type";
	private static final String TITLE_KEY = "title";
	private static final String MIN_KEY = "min";
	private static final String MAX_KEY = "max";
	private static final String PREPEND_KEY = "prepend";
	private static final String APPEND_KEY = "append";
	private static final String PATTERN_KEY = "pattern";
	private static final String PLACEHOLDER_KEY = "placeholder";
	private static final String READONLY_KEY = "readonly";
	private static final String REQUIRED_KEY = "required";
	private static final String NOTITLE_KEY = "noTitle";
	private static final String DISABLED_KEY = "disabled";
	private static final String CSS_CLASS_KEY = "cssClass";
	private static final String INPUT_CSS_CLASS_KEY = "inputCssClass";
	private static final String DEFAULT_VALUE_KEY = "defaultValue";
	private static final String ENUM_VALUES_KEY = "enumValues";
	private static final String CONDITION_KEY = "condition";
	
	private Type type;
	private String title;
	private String inlineTitle;
	private String uiType;
	private Integer min;
	private Integer max;
	private Boolean readOnly;
	private Boolean required;
	private Boolean noTitle;
	private Boolean disabled;
	private String prepend;
	private String append;
	private String pattern;
	private String placeholder;
	private String cssClass;
	private String inputCssClass;
	private Object defaultValue;
	private Object[] enumValues;
	private String condition;
	
	private FormField( String path, String key, String javaDoc )
	{
		super( path, key, javaDoc );
	}
	
	public static boolean isConvertable( Element element )
	{
		return AnnotationUtils.hasAnnotation(element, AnnotationUtils.CONVERTABLE_ANNOTATION);
	}
	
	public static FormField create( Element element, TypeMirror type, TypeUtils typeUtils, String configPath, Object defaultValue, Object...enumValues )
	{
		FormField input = getDefaultInput( element, type, typeUtils, configPath, defaultValue, enumValues );
		
		AnnotationMirror annotation = AnnotationUtils.getAnnotation(element, AnnotationUtils.FORM_FIELD_ANNOTATION);
		if ( annotation != null )
		{
			input.init(annotation);
		}
		
		return input;
	}
		
	@Override
	public JSONObject toJSONSchema() throws JSONException
	{
		JSONObject object = new JSONObject();
		putIfNonNull( object, "type", type );
		putIfNonNull( object, "description", getJavaDoc() );
		putIfNonNull( object, "readOnly", readOnly );
		putIfNonNull( object, "required", required );
		putIfNonNull( object, "allowEmpty", disabled );
		putIfNonNull( object, "pattern", pattern );
		putIfNonNull( object, "default", defaultValue );
		putIfNonNull( object, "title", title );
		
		if ( enumValues != null && enumValues.length>0 )
		{
			JSONArray enums = new JSONArray();
			for ( Object enumValue : enumValues )
			{
				enums.put(enumValue);
			}
			object.put("enum", enums);
		}
		
		if ( type == Type.String )
		{
			putIfNonNull( object, "minLength", min );
			putIfNonNull( object, "maxLength", max );
		}
		else if ( type == Type.Integer || type == Type.Number )
		{
			putIfNonNull( object, "minimum", min );
			putIfNonNull( object, "maximum", max );
		}
		
		return object;
	}
	
	@Override
	public JSONObject toJSONLayout() throws JSONException
	{
		JSONObject object = new JSONObject();

		putIfNonNull( object, "key", getFullPath() );
		putIfNonNull( object, "type", uiType );
		putIfNonNull( object, "inlinetitle", inlineTitle );
		putIfNonNull( object, "prepend", prepend );
		putIfNonNull( object, "append", append );
		putIfNonNull( object, "placeholder", placeholder );
		putIfNonNull( object, "htmlClass", cssClass );
		putIfNonNull( object, "fieldHtmlClass", inputCssClass );
		putIfNonNull( object, "notitle", noTitle );
		putIfNonNull( object, "disabled", disabled );
		putIfNonNull( object, "condition", condition );
		
		if ( enumValues != null )
		{
			JSONArray titleMap = new JSONArray();
			for ( Object enumValue : enumValues )
			{
				JSONObject enumItem = new JSONObject();
				enumItem.put("name", enumValue );
				enumItem.put("value", enumValue );
				titleMap.put(enumItem);
			}
			object.put("titleMap", titleMap);
		}
		
		return object;
	}
	
	@Override
	protected void init( AnnotationMirror annotation )
	{
		super.init( annotation );
		
		Map<String,Object> properties = AnnotationUtils.getAnnotationValues(annotation);
		for( Map.Entry<String, Object> me : properties.entrySet() )
		{
			final String key = me.getKey();
			switch( key )
			{
			case TITLE_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, TITLE_KEY, String.class ), value -> title=value );
				break;
			case CONDITION_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, CONDITION_KEY, String.class ), value -> condition=value );
				break;
			case PREPEND_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, PREPEND_KEY, String.class ), value -> prepend=value );
				break;
			case APPEND_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, APPEND_KEY, String.class ), value -> append=value );
				break;
			case PATTERN_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, PATTERN_KEY, String.class ), value -> pattern=value );
				break;
			case PLACEHOLDER_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, PLACEHOLDER_KEY, String.class ), value -> placeholder=value );
				break;
			case TYPE_KEY: setIfValid( AnnotationUtils.getPropertyValue(
					properties, TYPE_KEY, String.class ), value -> uiType=value );
				break;
			case MIN_KEY: setIfValid( AnnotationUtils.getPropertyValue(
							properties, MIN_KEY, Integer.class ), value -> min=value );
				break;
			case MAX_KEY: setIfValid( AnnotationUtils.getPropertyValue(
							properties, MAX_KEY, Integer.class ), value -> max=value );
				break;	
			case READONLY_KEY: setIfValid( AnnotationUtils.getPropertyValue(
							properties, READONLY_KEY, Boolean.class ), value -> readOnly=value );
				break;
			case REQUIRED_KEY: setIfValid( AnnotationUtils.getPropertyValue(
							properties, REQUIRED_KEY, Boolean.class ), value -> required=value );
				break;
			case NOTITLE_KEY: setIfValid( AnnotationUtils.getPropertyValue(
							properties, NOTITLE_KEY, Boolean.class ), value -> noTitle=value );
				break;
			case DISABLED_KEY: setIfValid( AnnotationUtils.getPropertyValue(
							properties, DISABLED_KEY, Boolean.class ), value -> disabled=value );
				break;
			case CSS_CLASS_KEY: setIfValid( AnnotationUtils.getPropertyValue(
							properties, CSS_CLASS_KEY, String.class ), value -> cssClass=value );
				break;
			case INPUT_CSS_CLASS_KEY: setIfValid( AnnotationUtils.getPropertyValue(
							properties, INPUT_CSS_CLASS_KEY, String.class ), value -> inputCssClass=value );
				break;
			case DEFAULT_VALUE_KEY: setIfValid( AnnotationUtils.getPropertyValue(
							properties, DEFAULT_VALUE_KEY, Object.class ), value -> defaultValue=value );
				break;
			case ENUM_VALUES_KEY: setIfValid( parseEnumValues( AnnotationUtils.getPropertyValue(
					properties, ENUM_VALUES_KEY, Object.class ) ), values -> enumValues=values );
				break;
			}
		}
	}
		
	private static Object[] parseEnumValues( Object values )
	{
		if ( values instanceof String )
		{
			String ss = (String) values;
			if ( !ss.isEmpty() )
			{
				return Arrays.stream(ss.split(","))
						.map(s -> s.trim())
						.toArray();
			}
		}
		return null;
	}
	
	private static String createKey( Element element )
	{
		return element.getSimpleName().toString();
	}
	
	private static FormField getDefaultInput( Element element, TypeMirror type, TypeUtils typeUtils, String configPath, Object defaultValue, Object...enumValues )
	{
		FormField formField = new FormField( configPath, createKey( element ), typeUtils.getJavaDoc(element) );
		if ( isConvertable( element ) )
		{
			formField.type = Type.String;
		}
		else
		{
			formField.type = Type.fromJavaType( typeUtils.getType( type ), Type.String );
			formField.defaultValue = defaultValue;
			formField.enumValues = enumValues;
		}

		// use capitalized key as default title
		String key = formField.getKey();
		if ( key != null )
		{
			formField.title = Character.toUpperCase(key.charAt(0)) + key.substring(1);
		}
		
		// suppress title but use 'inlinetitle' instead (for checkboxes)
		if ( formField.type == Type.Boolean )
		{
			formField.noTitle = true;
			formField.inlineTitle = formField.title;
		}
		
		return formField;
	}
		
	public static enum Type {
		String("string", "java.lang.String", "java.lang.CharSequence"),
		Boolean("boolean", "java.lang.Boolean"),
		Integer( "integer", "java.lang.Integer", "java.lang.Short" ),
		Number( "number", "java.lang.Float", "java.lang.Double", "java.lang.Number" );
		
		private List<String> javaType;
		private String type;
		
		private Type( String type, String...javaTypes )
		{
			this.type = type;
			this.javaType = Arrays.asList(javaTypes);
		}
		
		public static Type fromJavaType( String javaType, Type defaultType )
		{
			for ( Type type : values() )
			{
				if ( type.javaType.contains(javaType) )
				{
					return type;
				}
			}
			return defaultType;
		}
		
		public String toString()
		{
			return type;
		}
	}
}
