package com.landray.kmss.tic.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bouncycastle.util.encoders.Base64;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.landray.kmss.common.model.IBaseModel;
import com.landray.kmss.sys.formula.parser.FormulaParser;
import com.landray.kmss.sys.formula.parser.FormulaParserByJS;
import com.landray.kmss.sys.metadata.interfaces.ISysMetadataParser;
import com.landray.kmss.tic.core.mapping.service.ITicCoreMappingMetaParse;
import com.landray.kmss.util.SpringBeanUtil;
import com.landray.kmss.util.StringUtil;

import net.sf.json.JSONNull;

/**
 * 递归函数工具类
 * @author 严海星
 * 2018年10月23日
 */
public class RecursionUtil {
	//解析xml转json时xml属性参数的json标识
	final static String XML_2_JSON_ATTR_FLAG = "-";
	
	public static String paseJdbcIn(JSONArray in, JSONArray search) {
		if(in != null)
		{
			JSONArray array = new JSONArray();
			List<String> list = new ArrayList<>();
			for(int i = 0 ;i < in.size(); i++)
			{
				JSONObject jo = in.getJSONObject(i);
				JSONObject inJo = new JSONObject();
				inJo.put("name", jo.getString("columnName"));
				list.add(jo.getString("columnName"));
				inJo.put("title", "");
				inJo.put("type", jo.getString("ctype"));
				inJo.put("isoptional", !"checked".equals(jo.getString("required")));
				array.add(inJo);
			}
			if (search != null) {
				for (int i = 0; i < search.size(); i++) {
					JSONObject sjo = search.getJSONObject(i);
					if (!list.contains(sjo.getString("columnName"))) {
						JSONObject searchJo = new JSONObject();
						searchJo.put("name", sjo.getString("columnName"));
						searchJo.put("title", "");
						searchJo.put("type", sjo.getString("stypeData"));
						searchJo.put("isoptional",
								!"checked".equals(sjo.getString("required")));
						array.add(searchJo);
					}
				}
			}
			return array.toJSONString();
		}else
		{
			return "[]";
		}
	}

	public static String paseJdbcOut(JSONArray out) {
		if(out != null)
		{
			JSONArray array = new JSONArray();
			JSONArray child = new JSONArray();
			for(int i = 0; i < out.size(); i++)
			{
				JSONObject jo = out.getJSONObject(i);
				JSONObject outJo = new JSONObject();
				outJo.put("name", jo.getString("tagName"));
				outJo.put("title", "");
				String type = null;
				String ctype = jo.getString("ctype").toLowerCase();
				if("int".equals(ctype)||"double".equals(ctype)||"long".equals(ctype)||"float".equals(ctype)){
					type=ctype;
				}else if("bigint".equals(ctype)){
					type="int";
				}else{
					type="string";
				}
				outJo.put("type", type);
				outJo.put("isoptional","true");
				child.add(outJo);
			}
			JSONObject parentJo = new JSONObject();
			parentJo.put("name", "result");
			parentJo.put("title", "");
			parentJo.put("type", "arrayObject");
			parentJo.put("children", child);
			array.add(parentJo);
			return array.toJSONString();
		}else
		{
			return "[]";
		}
	}

	/**
	 * 解析xml转换为json对象
	 * @param xml
	 * @return
	 * @author 严海星
	 * 2018年12月7日
	 */
	public static JSONObject paseXmlToJson(String xml)
	{
		XMLSerializer xmlSerializer = new XMLSerializer();
		String result = xmlSerializer.read(xml).toString();
		JSONObject jo = JSON.parseObject(result);
		JSONObject transJo = new JSONObject();
		recursionRemoveJsonNamespace(jo,transJo);
		return transJo;
	}
	
	
	/**
	 * 去除json中的命名空间对象
	 * @param jo
	 * @param transJo
	 * @author 严海星
	 * 2018年12月7日
	 */
	private static void recursionRemoveJsonNamespace(JSONObject jo,JSONObject transJo)
	{
		Set<String> fieldNames = jo.keySet();
		for(String fieldName : fieldNames)
		{
			String keyName = null;
			if(fieldName.contains(":"))
			{
				keyName = fieldName.substring(fieldName.lastIndexOf(":")+1);
			}else
			{
				keyName = fieldName;
			}
			Object object = jo.get(fieldName);
			if(object instanceof JSONObject)
			{
				JSONObject childJo = jo.getJSONObject(fieldName);
				JSONObject childTransJo = new JSONObject();
				recursionRemoveJsonNamespace(childJo,childTransJo);
				transJo.put(keyName, childTransJo);
			}else if(object instanceof JSONArray)
			{
				JSONArray jsArray = jo.getJSONArray(fieldName);
				JSONArray childArray = new JSONArray();
				for(int i = 0; i < jsArray.size(); i++)
				{
					Object obj = jsArray.get(i);
					if(obj instanceof JSONObject){
						JSONObject childJo = (JSONObject) obj;
						JSONObject childTransJo = new JSONObject();
						recursionRemoveJsonNamespace(childJo,childTransJo);
						childArray.add(childTransJo);
					}else if (obj instanceof String){
						childArray.add(obj.toString());
					}
				}
				transJo.put(keyName, childArray);
			}else
			{
				if(!fieldName.startsWith("@xmlns"))
				{
					transJo.put(keyName, jo.getString(fieldName));
				}
			}
		}
	}
	
	/**
	 * 
	 * @param jo
	 * @param node
	 * @author 严海星
	 * 2018年10月23日
	 */
	public static void recursionPaseXmlToSetJsonValue(JSONObject jo,Element node)
	{
		List<Element> childElements = node.elements();
		if(childElements != null && childElements.size() > 0)
		{
			JSONObject up = new JSONObject();
			//start
			if(childElements.size() > 1 )
			{
				//收集标签名一样的element放进同一个list,用于json解析成jsonarray对象
				Map<String,List<Element>> map = new HashMap<String,List<Element>>();
				for(Element e : childElements)
				{
					List<Element> list = map.get(e.getName());
					if(list != null)
					{
						list.add(e);
					}else
					{
						List<Element> elist = new ArrayList<Element>();
						elist.add(e);
						map.put(e.getName(), elist);
					}
				}
				
				Set<String> keySet = map.keySet();
				for(String key : keySet)
				{
					List<Element> elist = map.get(key);
					//解析为jsonarray对象
					if(elist.size()>1)
					{
						JSONArray childArrayJo = new JSONArray();
						for(Element childArrayNode : elist)
						{
							if(childArrayNode.elements().size()>0)
							{
								JSONObject childJo = new JSONObject();
								recursionPaseXmlToSetJsonValue(childJo,childArrayNode);
								childArrayJo.add(childJo);
							}else
							{
								childArrayJo.add(childArrayNode.getStringValue());
							}
						}
						up.put(key, childArrayJo);
					}else
					{
						Element element = elist.get(0);
						if(element.elements().size()>0)
						{
							JSONObject childJo = new JSONObject();
							recursionPaseXmlToSetJsonValue(childJo,element);
							up.put(key, childJo);
						}else
						{
							up.put(key, element.getStringValue());
						}
					}
				}
				jo.put(node.getName(), up);
			}else
			{
				JSONObject childJo = new JSONObject();
				recursionPaseXmlToSetJsonValue(childJo,childElements.get(0));
				jo.put(node.getName(), childJo);
			}
			
			//end
		}
		else
		{
			jo.put(node.getName(), node.getStringValue());
		}
	}
	
	/**
	 * 
	 * @param path
	 * @param jo
	 * @param doc
	 * @author 严海星
	 * 2018年10月23日
	 * @throws Exception 
	 */
	public static void recursionPaseJsonToSetXmlValue(String path,JSONObject jo,Document doc) throws Exception
	{
		Set<String> keys = jo.keySet();
		for(String key : keys)
		{
			Object value = jo.get(key);
			String chilPath = path + "/" + key;
			if(value instanceof JSONObject)
			{
				JSONObject chilNode = (JSONObject) value;
				recursionPaseJsonToSetXmlValue(chilPath,chilNode,doc);
				
			}else if(value instanceof JSONArray || value instanceof List)
			{//如果是明细表需要复制多个节点进行值
				JSONArray arrayValue = JSON.parseArray(JSON.toJSONString(value));
				Node childNode = doc.selectSingleNode(chilPath);
				Element parentElement = childNode.getParent();
				parentElement.remove(childNode);
				for(int i = 0; i < arrayValue.size(); i++)
				{
					Object obj = arrayValue.get(i);
					Element childElement = (Element)childNode.clone();
					if(obj instanceof JSONObject){
						JSONObject chilNode = (JSONObject) obj;
						Document childDoc = DocumentHelper.parseText(childElement.asXML());
						recursionPaseJsonToSetXmlValue("//"+key,chilNode,childDoc);
						parentElement.add(childDoc.getRootElement());
					}else{//兼容单级数组
						childElement.setText(obj.toString());
						parentElement.add(childElement);
					}
				}
			}
			else
			{
				//在doc中根据路径获取具体xml标签节点进行设值
				Node selectSingleNode = doc.selectSingleNode(chilPath);
				if(selectSingleNode != null)
				{
					if(value instanceof net.sf.json.JSONArray || value instanceof JSONArray)
					{
						List<Object> list = (List<Object>) value;
						Element parentElement = selectSingleNode.getParent();
						parentElement.remove(selectSingleNode);
						for(Object o : list)
						{
							Element childElement = (Element)selectSingleNode.clone();
							//Document childDoc = DocumentHelper.parseText(childElement.asXML());
							//JSONObject chilNode = arrayValue.getJSONObject(i);
							//recursionPaseJsonToSetXmlValue("//"+key,chilNode,childDoc);
							childElement.setText(o.toString());
							parentElement.add(childElement);
						}
					}else{
						selectSingleNode.setText(value.toString());
					}
				}
			}
			
		}
	}
	
	/**
	 * 递归把xml转化为JSON
	 * @param node
	 * @param nodeJo
	 * @author 严海星
	 * 2019年3月19日
	 */
	@SuppressWarnings("unchecked")
	private static void recursionPaseXmlToJSON(Element node,JSONObject nodeJo){
		List<Element> elements = node.elements();
		List<Attribute> attributes = node.attributes();
		if(elements.size()>0){
			if(elements.size()>1){//多个子元素
				//该map用于区分xml子元素是转换成jsonObject还是jsonArray
				Map<String,List<Element>> elementMap = new HashMap<String,List<Element>>();
				for(Element childElement: elements){
					List<Element> elementList = elementMap.get(childElement.getName());
					if(elementList != null){
						elementList.add(childElement);
					}else{
						elementList = new ArrayList<Element>();
						elementList.add(childElement);
						elementMap.put(childElement.getName(), elementList);
					}
				}
				Set<String> keySet = elementMap.keySet();
				JSONObject childJo = new JSONObject(true);
				for(Attribute a: attributes){
					childJo.put(XML_2_JSON_ATTR_FLAG+a.getName(), a.getValue());
				}
				for(String name : keySet){
					List<Element> elementList = elementMap.get(name);
					if(elementList.size()>1){//JSONArray
						JSONArray array = new JSONArray();
						for(Element ele : elementList){
							List<Element> childEles = ele.elements();
							if(childEles.size()>0){
								JSONObject childEleJo = new JSONObject();
								for(Element childEle : childEles){
									recursionPaseXmlToJSON(childEle, childEleJo);
								}
								array.add(childEleJo);
							}else{
								array.add(ele.getText());
							}
						}
						childJo.put(name, array);
						/*Element ele = elementList.get(0);
						List<Element> childEles = ele.elements();
						JSONArray array = new JSONArray();
						if(childEles.size()>0){
							JSONObject currJo = new JSONObject(true);
							for(Element childEle : childEles){
								recursionPaseXmlToJSON(childEle,currJo);
							}
							array.add(currJo);
						}
						childJo.put(name, array);*/
					}else{//JSONObject
						Element ele = elementList.get(0);
						recursionPaseXmlToJSON(ele,childJo);
					}
				}
				nodeJo.put(node.getName(), childJo);
			}else{//只有一个子元素
				Element childNode = elements.get(0);
				JSONObject childJo = new JSONObject(true);
				for(Attribute a: attributes){
					childJo.put(XML_2_JSON_ATTR_FLAG+a.getName(), a.getValue());
				}
				recursionPaseXmlToJSON(childNode,childJo);
				nodeJo.put(node.getName(), childJo);
			}
		}else{
			if(attributes.size()>0){
				JSONObject attrJo = new JSONObject();
				for(Attribute a: attributes){
					attrJo.put(XML_2_JSON_ATTR_FLAG+a.getName(), a.getValue());
				}
				nodeJo.put(node.getName(), attrJo);
			}else{
				nodeJo.put(node.getName(), node.getText());
			}
		}
	}
	
	/**
	 * 递归解析xml转化成json对象
	 * @param structure
	 * @param structureOj
	 * @author 严海星
	 * 2018年10月18日
	 */
	public static void recursionPaseStructureXmlToJSON(Element structure,JSONObject structureOj) {
		
		List<Element> fields = structure.selectNodes("field");
		if(fields != null && fields.size() > 0)
		{
			for(Element field : fields)
			{
				structureOj.put(field.attributeValue("name"), "");
			}
		}
		List<Element> structures = structure.selectNodes("structure");
		if(structures != null && structures.size() > 0)
		{
			for(Element structureNode : structures)
			{
				String structureName = structureNode.attributeValue("name");
				JSONObject structureChildJo = new JSONObject();
				recursionPaseStructureXmlToJSON(structureNode,structureChildJo);
				structureOj.put(structureName, structureChildJo);
			}
		}
		
	}
	
	/**
	 * 递归解析函数映射配置的json转换成用于函数调用的json
	 * @param toPaseJson
	 * @param transSetJson
	 * @author 严海星
	 * 2018年11月9日
	 */
	public static void recursionPaseJsonToTransSet(JSONArray toPaseArrayJson,JSONObject transSetJson,FormulaParserByJS formulaParserByJS)
	{
		for(int i = 0; i < toPaseArrayJson.size(); i++)
		{
			JSONObject toPaseJson = toPaseArrayJson.getJSONObject(i);
			String type = toPaseJson.getString("type");
			String fieldName = toPaseJson.getString("name");
			if("object".equals(type))
			{
				//object类型的使用JSONObject封装
				JSONObject childJo = new JSONObject();
				JSONArray children = toPaseJson.getJSONArray("children");
				recursionPaseJsonToTransSet(children,childJo,formulaParserByJS);
				transSetJson.put(fieldName, childJo);
				
			}else if("arrayObject".equals(type) || "array".equals(type))
			{

				JSONArray childArrayJo = toPaseJson.getJSONArray("children");
				JSONArray arrayJo = new JSONArray();
				
				List<List<Object>> allValueList = new ArrayList<List<Object>>();
				List<String> colNames = new ArrayList<String>();
				for(int j = 0; j < childArrayJo.size() ; j++)
				{
					JSONObject colJo = childArrayJo.getJSONObject(j);
					String colName = colJo.getString("name");
					//colNames.add(colName1);
					//通过公式定义器解析获取一个列字段值的List集合
					List<Object>  colValueList = null;
					//兼容table中含有object的字段
					String fieldType = colJo.getString("type");
					if("object".equals(fieldType))//暂时考虑一级嵌套object
					{
						List<List<Object>> childFieldValues = new ArrayList<List<Object>>();
						List<String> childColName = new ArrayList<String>();
						JSONArray colJoChildArrayJo = colJo.getJSONArray("children");
						//Set<String> childFieldNames = colJo.keySet();
						for(int cc = 0 ; cc < colJoChildArrayJo.size(); cc++)
						{
							JSONObject childFieldJo = colJoChildArrayJo.getJSONObject(cc);
							String childFieldName = childFieldJo.getString("name");
							String hiddenValueStr = childFieldJo.getString("hidden_value");
							String toGetValue = null;
							if(StringUtil.isNotNull(hiddenValueStr)){
								if(hiddenValueStr.contains("$")){
									toGetValue = hiddenValueStr;
								}else{//需要用base64解码
									toGetValue = new String(Base64.decode(hiddenValueStr));
								}
							}
							List<Object> formValue = (List<Object>) formulaParserByJS.parseValueScript(toGetValue,childFieldJo.getString("type"));
							List<Object> tempList = new ArrayList<Object>();
							if(formValue != null)
							{
								for(Object obj : formValue)
								{
									if(obj != null && !(obj instanceof JSONNull))
									{
										tempList.add(obj);
									}
								}
							}
							childFieldValues.add(tempList);
							//收集字段名称
							childColName.add(childFieldName);
						}
						
						//获取列表的行数  
						int rowNum  = childFieldValues.get(0).size();
						colValueList = new ArrayList<Object>();
						for(int c = 0 ; c < rowNum; c++)
						{
							JSONObject rowDataJo = new JSONObject();
							//列处理
							for(int k = 0 ; k < childFieldValues.size() ; k ++)
							{
								List<Object> colValues = childFieldValues.get(k);
								Object colValue = colValues.get(c);
								rowDataJo.put(childColName.get(k), colValue);
							}
							colValueList.add(rowDataJo);
						}
					//明细表嵌明细表,暂不考虑上级明细
					}else if("arrayObject".equals(fieldType) || "array".equals(fieldType)){
						
						JSONArray colJoChildArrayJo = colJo.getJSONArray("children");
						List<List<Object>> childAllValueList = new ArrayList<List<Object>>();
						List<String> childColNames = new ArrayList<String>();
						for(int k = 0; k < colJoChildArrayJo.size() ; k++){
							JSONObject colJoChildJo = colJoChildArrayJo.getJSONObject(k);
							String hiddenValueStr = colJoChildJo.getString("hidden_value");
							String toGetValue = null;
							if(StringUtil.isNotNull(hiddenValueStr)){
								if(hiddenValueStr.contains("$")){
									toGetValue = hiddenValueStr;
								}else{//需要用base64解码
									toGetValue = new String(Base64.decode(hiddenValueStr));
								}
							}
							
							//因为是明细中的明细,因此该value是两级数组,当前不考虑上级,因此获取值后只获取最后一级数组
							Object value = formulaParserByJS.parseValueScript(toGetValue,colJoChildJo.getString("type"));
							if(value != null && !(value instanceof JSONNull) && value instanceof List){
								List<Object> tempList  = (List<Object>) value;
								if(tempList.size() == 1){
									Object obj = tempList.get(0);
									if(obj != null){
										List<Object> rl = null;
										if(obj instanceof List){
											rl = (List<Object>) obj; 
										}else if(!(obj instanceof JSONNull)){
											rl = new ArrayList<Object>();
											rl.add(obj);
										}
										if(rl != null){
											childAllValueList.add(rl);
											childColNames.add(colJoChildJo.getString("name"));
										}
									}
								}else{
									if(tempList.size() > 0){
										if(!(tempList.size() == 1 && tempList.get(0) instanceof JSONNull)){
											childAllValueList.add(tempList);
											childColNames.add(colJoChildJo.getString("name"));
										}
									}
								}
							}
						}
						
						if(childAllValueList.size() > 0){
							//获取列表的行数  *由于表单数据中的table列数据存在单值情况,所以需要下方法获取整体的行数
							List<Integer> selectRowNum = new ArrayList<Integer>();
							for(List<Object> valueList : childAllValueList)
							{
								selectRowNum.add(valueList.size());
							}
							Collections.sort(selectRowNum);
							int rowNum  = selectRowNum.get(selectRowNum.size()-1);
							
							List<Object> rowDataList = new ArrayList<Object>();
							
							for(int c = 0 ; c < rowNum; c++)
							{
								JSONObject rowDataJo = new JSONObject();
								//列处理
								for(int k = 0 ; k < childAllValueList.size() ; k ++)
								{
									List<Object> colValues = childAllValueList.get(k);
									Object colValue = null;
									if(colValues.size() > 0){
										//预防表单数据中的table对象中列值只有一个值
										if(colValues.size()==1){
											colValue = colValues.get(0);
										}else{
											colValue = colValues.get(c);
										}
										if(colValue != null && !(colValue instanceof JSONNull)){
											rowDataJo.put(childColNames.get(k), colValue);
										}
									}
								}
								rowDataList.add(rowDataJo);
							}
							colValueList = new ArrayList<Object>();
							colValueList.add(rowDataList);
						}
					}else
					{
						String realType;
						String valueType = colJo.getString("type");
						if(valueType.startsWith("array")){
							realType = valueType.replace("array", "");
						}else{
							realType = valueType;
						}
						
						String hiddenValueStr = colJo.getString("hidden_value");
						String toGetValue = null;
						if(StringUtil.isNotNull(hiddenValueStr)){
							if(hiddenValueStr.contains("$")){
								toGetValue = hiddenValueStr;
							}else{//需要用base64解码
								toGetValue = new String(Base64.decode(hiddenValueStr));
							}
						}
						Object parseValueScript = formulaParserByJS.parseValueScript(toGetValue,realType);
						if(parseValueScript != null && !(parseValueScript instanceof JSONNull)){
							if(parseValueScript instanceof List){
								List<Object> tempList = (List<Object>) parseValueScript;
								for(Object obj : tempList)
								{
									if(obj != null && !(obj instanceof JSONNull) && StringUtil.isNotNull(obj.toString()))
									{
										if(colValueList==null){
											colValueList = new ArrayList<Object>();
										}
										colValueList.add(obj);	
									}
								}
							}else{
								colValueList = new ArrayList<Object>();
								colValueList.add(parseValueScript);
							}
						}
					}
					if(colValueList != null){
						colNames.add(colName);
						allValueList.add(colValueList);
					}
				}
				
				//获取列表的行数
				List<Integer> childRowNum = new ArrayList<Integer>();
				for(List<Object> colList : allValueList)
				{
					childRowNum.add(colList.size());
				}
				Collections.sort(childRowNum);
				//获取明细表的行号
				int rowNum  = childRowNum.get(childRowNum.size()-1);
				
				for(int r = 0; r < rowNum; r++)
				{
					//列表的行数据json
					JSONObject rowJo = new JSONObject();
					for(int k = 0; k < allValueList.size(); k++)
					{
						List<Object> colValueList = allValueList.get(k);
						Object colValue = colValueList.get(r);
						//行数据中的列数据设值
						rowJo.put(colNames.get(k), colValue);
					}
					arrayJo.add(rowJo);
				}
				transSetJson.put(fieldName, arrayJo);
				
			}else//String类型的转换字段
			{
				//公式定义器获取实际值
				String hiddenValueStr = toPaseJson.getString("hidden_value");
				String toGetValue = null;
				if(StringUtil.isNotNull(hiddenValueStr)){
					if(hiddenValueStr.contains("$")){
						toGetValue = hiddenValueStr;
					}else{//需要用base64解码
						toGetValue = new String(Base64.decode(hiddenValueStr));
					}
				}
				Object value = formulaParserByJS.parseValueScript(toGetValue,toPaseJson.getString("type"));
				if(value != null && !"null".equals(value.toString()))
					transSetJson.put(fieldName, value);
			}
		}
	}
	
	/**
	 * 解析json数据,转换成入参的json格式数据,该方法用于restFul集成函数
	 * @param jsonStr
	 * @return
	 * @author 严海星
	 * 2019年1月18日
	 */
	public static String paseJsonTransParamInJson(String jsonStr){
		JSONObject paseJo = JSON.parseObject(jsonStr);
		LinkedHashMap<String, String> jsonMap = JSON.parseObject(jsonStr, new TypeReference<LinkedHashMap<String, String>>() {});
		JSONArray paramIn = new JSONArray();
		recursionPaseJsonTransParamInJson(jsonMap,paseJo,paramIn);
		return JSON.toJSONStringZ(paramIn, SerializeConfig.getGlobalInstance(), SerializerFeature.QuoteFieldNames);
	}
	
	/**
	 * 解析xml数据,转化成入参的json数据格式,该方法用于NC集成函数
	 * @param xml
	 * @return
	 * @author 严海星
	 * 2019年3月18日
	 * @throws Exception 
	 */
	public static String paseXMLTransParamInJson(String xml) throws Exception{
		Document document = DocumentHelper.parseText(xml);
		JSONObject inParam = new JSONObject(true);
		recursionPaseXmlToJSON(document.getRootElement(),inParam);
		//return JSON.toJSONStringZ(inParam, SerializeConfig.getGlobalInstance(), SerializerFeature.QuoteFieldNames);
		return paseJsonTransParamInJson(inParam.toJSONString());
	}
	
	/**
	 * 
	 * @param xml
	 * @return
	 * @throws Exception
	 * @author 严海星
	 * 2019年3月21日
	 */
	public static String paseXMLTransJson(String xml) throws Exception{
		Document document = DocumentHelper.parseText(xml);
		JSONObject inParam = new JSONObject();
		recursionPaseXmlToJSON(document.getRootElement(),inParam);
		return inParam.toJSONString();
	}
	
	/**
	 * 解析json数据,转换成ParamIn,该方法用于rest模块
	 * @param jsonMap
	 * @param paseJo
	 * @param paramIn
	 * @author 严海星
	 * 2019年1月18日
	 */
	public static void recursionPaseJsonTransParamInJson(LinkedHashMap<String, String> jsonMap,JSONObject paseJo,JSONArray paramIn){
		for (Map.Entry<String, String> entry : jsonMap.entrySet()) {
			String fieldName = entry.getKey();
			Object obj = paseJo.get(fieldName);
			if(obj instanceof JSONObject){
				JSONObject fieldJo = new JSONObject(true);
				fieldJo.put("name",fieldName);
				fieldJo.put("title",fieldName);
				fieldJo.put("type", "object");
				JSONArray children = new JSONArray();
				LinkedHashMap<String, String> childJsonMap = JSON.parseObject(entry.getValue(), new TypeReference<LinkedHashMap<String, String>>() {});
				recursionPaseJsonTransParamInJson(childJsonMap,(JSONObject)obj,children);
				fieldJo.put("children", children);
				paramIn.add(fieldJo);
			}else if(obj instanceof JSONArray){
				JSONObject fieldJo = new JSONObject(true);
				fieldJo.put("name",fieldName);
				fieldJo.put("title",fieldName);
				JSONArray children = null;
				JSONArray objArray = (JSONArray) obj;
				if(objArray.size()==0){//如果是空数组默认数组类型为arrayString
					fieldJo.put("type", "arrayString");
				}else{
					Object childObj = objArray.get(0);
					if(childObj != null){
						if(childObj instanceof JSONObject){
							fieldJo.put("type", "arrayObject");
							children = new JSONArray();
							LinkedHashMap<String, String> childJsonMap = JSON.parseObject(childObj.toString(), new TypeReference<LinkedHashMap<String, String>>() {});
							recursionPaseJsonTransParamInJson(childJsonMap,(JSONObject) childObj,children);
						}else if(childObj instanceof JSONArray){
							/*JSONObject childFieldJo = new JSONObject();
						JSONArray arrayChil = new JSONArray();
						paseArrayJsonTransParamInJson((JSONArray)childObj,arrayChil);*/
						}else{//单级数组
							String value = childObj.toString();
							if(StringUtil.isNull(value)){
								fieldJo.put("type", "arrayString");
							}else if(DataTypeUtil.isInt(value)){
								fieldJo.put("type", "arrayInt");
							}else if(DataTypeUtil.isDouble(value)){
								fieldJo.put("type", "arrayDouble");
							}else if(DataTypeUtil.isBoolean(value)){
								fieldJo.put("type", "arrayBoolean");
							}else{
								fieldJo.put("type", "arrayString");
							}
						}
					}
					if(children != null){
						fieldJo.put("children", children);
					}
				}
				paramIn.add(fieldJo);
			}else{
				String type;
				String value = obj.toString();
				if(StringUtil.isNull(value)){
					type = "string";
				}else if(DataTypeUtil.isInt(value)){
					type = "int";
				}else if(DataTypeUtil.isDouble(value)){
					type = "double";
				}else if(DataTypeUtil.isBoolean(value)){
					type = "boolean";
				}else{
					type = "string";
				}
				JSONObject fieldJo = new JSONObject(true);
				fieldJo.put("name",fieldName);
				fieldJo.put("title",fieldName);
				fieldJo.put("type", type);
				paramIn.add(fieldJo);
			}
		}
	}
	
	/**
	 * 原始函数返回值的传出转换
	 * @param formJson
	 * @param transsetOutJson
	 * @param formulaParser
	 * @author 严海星
	 * 2019年2月18日
	 */
	public static void parserRtnJsonToTranssetOutJsonData(JSONArray formJson, JSONObject transsetOutJson,
			FormulaParserByJS formulaParser)
	{
		for(int i = 0 ;i < formJson.size(); i++)
		{
			JSONObject fieldJo = formJson.getJSONObject(i);
			String fieldName = fieldJo.getString("name");
			String type = fieldJo.getString("type");
			if("arrayObject".equals(type) || "array".equals(type))
			{
				JSONArray childArrayJo = fieldJo.getJSONArray("children");
				//JSONObject childTransJo = new JSONObject();
				if(childArrayJo != null)
				{
					JSONArray childJo = new JSONArray();
					List<List<String>> allValueList = new ArrayList<List<String>>();
					List<String> colNameList = new ArrayList<String>();
					
					for(int j = 0; j < childArrayJo.size(); j++)
					{
						JSONObject childFieldJo = childArrayJo.getJSONObject(j);
						String childType = childFieldJo.getString("type");
						String childFieldName = childFieldJo.getString("name");
						List<String> valueList = null;
						if("object".equals(childType))
						{//明细表中嵌套对象
							JSONArray childChildArrayJo = childFieldJo.getJSONArray("children");
							List<List<String>> chilValueList = new ArrayList<List<String>>();
							List<String> chilColNameList = new ArrayList<String>();
							for(int k = 0; k < childChildArrayJo.size(); k++)
							{
								JSONObject jo = childChildArrayJo.getJSONObject(k);
								String chilFieldName = jo.getString("name");
								
								String hiddenValueStr = jo.getString("hidden_value");
								String valueScript = null;
								if(StringUtil.isNotNull(hiddenValueStr)){
									if(hiddenValueStr.contains("$")){
										valueScript = hiddenValueStr;
									}else{//需要用base64解码
										valueScript = new String(Base64.decode(hiddenValueStr));
									}
								}
								
								if(StringUtil.isNotNull(valueScript)&&valueScript.contains(":"))
								{
									valueScript = recursionDelNamespaces(valueScript,0);
								}
								List<String> childValueList = null;
								Object value = formulaParser.parseValueScript(valueScript,jo.getString("type"));
								if(value != null && !(value instanceof JSONNull) && !"null".equals(value.toString()))
								{
									if(value instanceof List)
									{
										List<Object> vlist = (List<Object>)value;
										List<String> hasValueList = new ArrayList<String>();
										for(Object obj : vlist)
										{
											if(obj != null && !(obj instanceof JSONNull))
											{
												hasValueList.add((String)obj);
											}
										}
										if(hasValueList.size()>0)
										{
											childValueList = hasValueList;
										}
										
									}else
									{
										childValueList = new ArrayList<String>();
										childValueList.add(value.toString());
									}
									if(childValueList != null)
									{
										chilColNameList.add(chilFieldName);
										chilValueList.add(childValueList);
									}
								}
							}
							if(chilValueList.size() > 0)
							{
								//start 明细中嵌套的对象数据处理
								List<Integer> childRowNum = new ArrayList<Integer>();
								for(List<String> colList : chilValueList)
								{
									childRowNum.add(colList.size());
								}
								Collections.sort(childRowNum);
								//获取明细表的行号
								int rowNum  = childRowNum.get(childRowNum.size()-1);
								//保存
								valueList = new ArrayList<String>();
								for(int r = 0; r < rowNum; r++)
								{
									JSONObject rowJo = new JSONObject();
									for(int c = 0; c < chilValueList.size(); c++)
									{
										List<String> colList = chilValueList.get(c);
										String colName = chilColNameList.get(c);
										String colValue = null;
										colValue = colList.get(r);
										if(StringUtil.isNotNull(colValue))
										{
											rowJo.put(colName, colValue);
										}else
										{
											rowJo.put(colName, colList.get(0));
										}
									}
									valueList.add(rowJo.toJSONString());
								}
								//end 明细中嵌套的对象数据处理
							}
						}else if("arrayObject".equals(childType) || "array".equals(childType))//*还未经测试
						{//明细表中嵌套明细
							/*JSONArray childChildArrayJo = childFieldJo.getJSONArray("children");
							List<List<List<String>>> chilValueList = new ArrayList<List<List<String>>>();
							List<String> chilColNameList = new ArrayList<String>();
							for(int k = 0 ; k < childChildArrayJo.size() ; k++)
							{
								JSONObject jo = childChildArrayJo.getJSONObject(k);
								String chilFieldName = jo.getString("name");
								String valueScript = jo.getString("hidden_value");
								
								if(StringUtil.isNotNull(valueScript)&&valueScript.contains(":"))
								{
									valueScript = recursionDelNamespaces(valueScript,0);
								}
								List<List<String>> childValueList = null;
								Object value = formulaParser.parseValueScript(valueScript);
								if(value != null && !(value instanceof JSONNull) && !"null".equals(value.toString()))
								{   //[["test1_list_a1_1","test1_list_a1_2"],["test2_list_a1_1","test2_list_a1_2"]]
									if(value instanceof List)
									{
										List<Object> vlist = (List<Object>)value;
										List<List<String>> hasValueList = new ArrayList<List<String>>();
										List<String> tempList = null;
										for(Object obj : vlist)
										{
											if(obj != null && !(obj instanceof JSONNull) && obj instanceof List)
											{
												if(!(((List)obj).get(0) instanceof JSONNull))
												hasValueList.add((List)obj);
											}else if(obj instanceof String){
												if(tempList == null)
												{
													tempList = new ArrayList<String>();
												}
												tempList.add(obj.toString());
											}
										}
										if(hasValueList.size()>0)
										{
											childValueList = hasValueList;
										}
										if(tempList != null)
										{
											if(childValueList == null)
												childValueList = new ArrayList<List<String>>();
											childValueList.add(tempList);
										}
										
									}
									if(childValueList != null)
									{
										chilColNameList.add(chilFieldName);
										chilValueList.add(childValueList);
									}
								
								}
							}
							
							if(chilValueList.size() > 0)
							{
								//start 明细中嵌套明细数据处理
								List<Integer> childRowNum = new ArrayList<Integer>();
								for(List<List<String>> colList : chilValueList)
								{
									childRowNum.add(colList.size());
								}
								Collections.sort(childRowNum);
								//获取明细表的行号
								int rowNum  = childRowNum.get(childRowNum.size()-1);
								//保存
								valueList = new ArrayList<String>();
								for(int r = 0; r < rowNum; r++)
								{
									JSONObject rowJo = new JSONObject();
									for(int c = 0; c < chilValueList.size(); c++)
									{
										List<List<String>> colList = chilValueList.get(c);
										String colName = chilColNameList.get(c);
										Object colValue = null;
										colValue = colList.get(r);
										if(StringUtil.isNotNull(colValue.toString()))
										{
											rowJo.put(colName, colValue);
										}else
										{
											rowJo.put(colName, colList.get(0));
										}
									}
									valueList.add(rowJo.toJSONString());
								}
								//end 明细中嵌套明细数据处理
							}*/
							
						}else
						{//明细表中简单字段处理
							String hiddenValueStr = childFieldJo.getString("hidden_value");
							String valueScript = null;
							if(StringUtil.isNotNull(hiddenValueStr)){
								if(hiddenValueStr.contains("$")){
									valueScript = hiddenValueStr;
								}else{//需要用base64解码
									valueScript = new String(Base64.decode(hiddenValueStr));
								}
							}
							if(StringUtil.isNotNull(valueScript))
							{
								if(StringUtil.isNotNull(valueScript)&&valueScript.contains(":"))
								{
									valueScript = recursionDelNamespaces(valueScript,0);
								}
								Object value = formulaParser.parseValueScript(valueScript,childFieldJo.getString("type"));
								if(value != null && !(value instanceof JSONNull)&& !"null".equals(value.toString()))
								{
									if(value instanceof List)
									{
										List<Object> tempList = (List<Object>) value;
										boolean tag = false;
										for(Object obj : tempList){
											if(obj != null && StringUtil.isNotNull(obj.toString()) && !(obj instanceof JSONNull)&& !"null".equals(obj.toString())){
												tag = true;
											}
										}
										//childFieldValues.add((List<String>)value);
										if(tag){
											valueList = new ArrayList<String>();
											for(Object obj : tempList)
											{
												if(obj != null && StringUtil.isNotNull(obj.toString()) && !(obj instanceof JSONNull)&& !"null".equals(obj.toString()))
												{
													valueList.add(obj.toString());
												}else{
													valueList.add("");
												}
											}
										}
									}else
									{
										valueList = new ArrayList<String>();
										valueList.add(value.toString());
										//childFieldValues.add(valueList);
									}
								}
							}
						}
						if(valueList != null)
						{
							colNameList.add(childFieldName);
							allValueList.add(valueList);
						}
					}
					if(allValueList.size() > 0)
					{
						//获取列表的行数  *由于表单数据中的table列数据存在单值情况,所以需要下方法获取整体的行数
						List<Integer> selectRowNum = new ArrayList<Integer>();
						for(List<String> colList : allValueList)
						{
							selectRowNum.add(colList.size());
						}
						Collections.sort(selectRowNum);
						//获取明细表的行号
						int rowNum  = selectRowNum.get(selectRowNum.size()-1);
						
						for(int r = 0; r < rowNum; r++)
						{
							JSONObject rowJo = new JSONObject();
							for(int c = 0; c < allValueList.size(); c++)
							{
								List<String> colList = allValueList.get(c);
								String colName = colNameList.get(c);
								String colValue = colList.get(r);
								if(null != colValue)
								{
									rowJo.put(colName, colValue);
								}else
								{
									rowJo.put(colName, colList.get(0));
								}
							}
							childJo.add(rowJo);
						}
						transsetOutJson.put(fieldName, childJo);
					}else
					{
						transsetOutJson.put(fieldName, childJo);
					}
				}
				/*parserFormJsonDataToTranssetOutJsonData(childArrayJo,childTransJo,formulaParser);
				childJo.add(childTransJo);*/
				
			}else if("object".equals(type))
			{
				JSONArray childArrayJo = fieldJo.getJSONArray("children");
				JSONObject childJo = new JSONObject();
				if(childArrayJo != null)
					parserRtnJsonToTranssetOutJsonData(childArrayJo,childJo,formulaParser);
				transsetOutJson.put(fieldName, childJo);
			}else
			{
				String hiddenValueStr = fieldJo.getString("hidden_value");
				String valueScript = null;
				if(StringUtil.isNotNull(hiddenValueStr)){
					if(hiddenValueStr.contains("$")){
						valueScript = hiddenValueStr;
					}else{//需要用base64解码
						valueScript = new String(Base64.decode(hiddenValueStr));
					}
				}
				if(StringUtil.isNotNull(valueScript)&&valueScript.contains(":"))
				{
					valueScript = recursionDelNamespaces(valueScript,0);
				}
				
				/*if("arrayBoolean".equals(type)){
					String realType = type.replace("array", "").toLowerCase();
				}else if("arrayString".equals(type)){
					String realType = type.replace("array", "").toLowerCase();
				}else if("arrayNumber".equals(type)){
					String realType = type.replace("array", "").toLowerCase();*/
				
				/*if(type.startsWith("array")){
					String realType = type.replace("array", "").toLowerCase();
					Object value = formulaParser.parseValueScript(valueScript,realType);
					if(value != null && !"null".equals(value.toString()) && !(value instanceof JSONNull)){
						
					}
				}else{
					Object value = formulaParser.parseValueScript(valueScript,type);
					if(value != null && !"null".equals(value.toString()) && !(value instanceof JSONNull))
						transsetOutJson.put(fieldName, value);
				}*/
				String realType;
				if(type.startsWith("array")){
					realType = type.replace("array", "").toLowerCase();
				}else{
					realType = type;
				}
				Object value = formulaParser.parseValueScript(valueScript,realType);
				if(value != null && !"null".equals(value.toString()) && !(value instanceof JSONNull))
					
					if(type.startsWith("array")){//单层数组类型处理
						List<String> list = (List<String>) value;
						if("boolean".equals(realType)){
							List<Boolean> realValue = new ArrayList<Boolean>();
							for(String s : list){
								realValue.add(Boolean.valueOf(s));
							}
							transsetOutJson.put(fieldName, realValue);
						}else if("int".equals(realType)){
							List<Integer> realValue = new ArrayList<Integer>();
							for(String s : list){
								realValue.add(Integer.valueOf(s));
							}
							transsetOutJson.put(fieldName, realValue);
						}else if("double".equals(realType)){
							List<Double> realValue = new ArrayList<Double>();
							for(String s : list){
								realValue.add(Double.valueOf(s));
							}
							transsetOutJson.put(fieldName, realValue);
						}else{
							transsetOutJson.put(fieldName, value);
						}
					}else{
						transsetOutJson.put(fieldName, value);
					}
			}
			
		}
	}
	
	/**
	 * 递归去除公式定义器处理函数返回值时,去除公式表达式中的命名空间
	 * @param key
	 * @return
	 * @author 严海星
	 * 2018年11月22日
	 */
	public static String recursionDelNamespaces(String key,int index)
	{
		int one = key.indexOf("$",index);
		int two = key.indexOf("$",one + 1);
		if((one > 0 || index == 0) && two > 0)
		{
			String subStr = key.substring(one+1, two);
			String temp = subStr;
			Matcher m = Pattern.compile("\\.(.*?):").matcher(subStr);
			while(m.find()){
				temp = temp.replaceAll(m.group(1)+":", "");
			}
			if(temp.contains(":"))
			{
				String substr = temp.substring(0, temp.indexOf(":")+1);
				temp = temp.replaceAll(substr, "");
			}
			key = key.replace(subStr, temp);
			//再重新获取替换后的index			
			one = key.indexOf("$",index);
			two = key.indexOf("$",one + 1);
			key = recursionDelNamespaces(key,two+1);
		}
		return key;
	}
	
	/**
	 * 解析表单映射中出参的json并通过公式定义器计算把值传到表单
	 * @param cfExportJo
	 * @param oldOutformulaParser
	 * @param sysMetadataParser
	 * @author 严海星
	 * 2018年11月14日
	 */
	public static void paseCfExportToForm(JSONArray cfExportJo, FormulaParser oldOutformulaParser,
			ISysMetadataParser sysMetadataParser,IBaseModel mainModel) {
		for(int i = 0 ;i < cfExportJo.size() ;i++)
		{
			JSONObject fieldJo = cfExportJo.getJSONObject(i);
			String fieldName = fieldJo.getString("name");
			String childStr = fieldJo.getString("children");
			if(StringUtil.isNotNull(childStr))
			{
				JSONArray childArray = fieldJo.getJSONArray("children");
				recursionPaseCfJsonToForm(fieldName, childArray, oldOutformulaParser, sysMetadataParser, mainModel);
			}else
			{
				try {
					String hiddenValueStr = fieldJo.getString("hidden_value");
					String toGetValue = null;
					if(StringUtil.isNotNull(hiddenValueStr)){
						if(hiddenValueStr.contains("$")){
							toGetValue = hiddenValueStr;
						}else{//需要用base64解码
							toGetValue = new String(Base64.decode(hiddenValueStr));
						}
					}
					Object value = oldOutformulaParser.parseValueScriptSpecial(toGetValue);
					if(value != null && !"null".equals(value.toString()))
						sysMetadataParser.setFieldValue(mainModel, fieldName, value);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 重载方法,用于流程事件
	 * @param cfExportJo
	 * @param oldOutformulaParser
	 * @param sysMetadataParser
	 * @param mainModel
	 * @author 严海星
	 * 2018年12月28日
	 */
	public static void paseCfExportToForm(JSONArray cfExportJo, FormulaParser oldOutformulaParser,
			ITicCoreMappingMetaParse sysMetadataParser,IBaseModel mainModel) {
		for(int i = 0 ;i < cfExportJo.size() ;i++)
		{
			JSONObject fieldJo = cfExportJo.getJSONObject(i);
			String fieldName = fieldJo.getString("name");
			String childStr = fieldJo.getString("children");
			if(StringUtil.isNotNull(childStr))
			{
				JSONArray childArray = fieldJo.getJSONArray("children");
				recursionPaseCfJsonToForm(fieldName, childArray, oldOutformulaParser, sysMetadataParser, mainModel);
			}else
			{
				try {
					String hiddenValueStr = fieldJo.getString("hidden_value");
					String toGetValue = null;
					if(StringUtil.isNotNull(hiddenValueStr)){
						if(hiddenValueStr.contains("$")){
							toGetValue = hiddenValueStr;
						}else{//需要用base64解码
							toGetValue = new String(Base64.decode(hiddenValueStr));
						}
					}
					Object value = oldOutformulaParser.parseValueScriptSpecial(toGetValue);
					if(value != null && !"null".equals(value.toString()))
						sysMetadataParser.setFieldValue(mainModel, fieldName, value);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 递归解析cfJson并通过公式定义器计算把值传到表单
	 * @param parentName
	 * @param childArray
	 * @param oldOutformulaParser
	 * @param sysMetadataParser
	 * @param mainModel
	 * @author 严海星
	 * 2018年11月14日
	 */
	public static void recursionPaseCfJsonToForm(String parentName,JSONArray childArray,FormulaParser oldOutformulaParser,
			ISysMetadataParser sysMetadataParser,IBaseModel mainModel)
	{
		for(int i = 0;i < childArray.size();i++)
		{
			JSONObject fieldJo = childArray.getJSONObject(i);
			String fieldName = fieldJo.getString("name");
			String childStr = fieldJo.getString("children");
			if(StringUtil.isNotNull(childStr))
			{
				JSONArray childArrayJo = fieldJo.getJSONArray("children");
				recursionPaseCfJsonToForm(parentName+"."+fieldName, childArrayJo, oldOutformulaParser, sysMetadataParser, mainModel);
			}else
			{
				try {
					String hiddenValueStr = fieldJo.getString("hidden_value");
					String toGetValue = null;
					if(StringUtil.isNotNull(hiddenValueStr)){
						if(hiddenValueStr.contains("$")){
							toGetValue = hiddenValueStr;
						}else{//需要用base64解码
							toGetValue = new String(Base64.decode(hiddenValueStr));
						}
					}
					Object value = oldOutformulaParser.parseValueScript(toGetValue);
					sysMetadataParser.setFieldValue(mainModel, parentName+"."+fieldName, value);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	/**
	 * 重载方法,用于流程事件
	 * @param parentName
	 * @param childArray
	 * @param oldOutformulaParser
	 * @param sysMetadataParser
	 * @param mainModel
	 * @author 严海星
	 * 2018年12月28日
	 */
	public static void recursionPaseCfJsonToForm(String parentName,JSONArray childArray,FormulaParser oldOutformulaParser,
			ITicCoreMappingMetaParse sysMetadataParser,IBaseModel mainModel)
	{
		for(int i = 0;i < childArray.size();i++)
		{
			JSONObject fieldJo = childArray.getJSONObject(i);
			String fieldName = fieldJo.getString("name");
			String childStr = fieldJo.getString("children");
			if(StringUtil.isNotNull(childStr))
			{
				JSONArray childArrayJo = fieldJo.getJSONArray("children");
				recursionPaseCfJsonToForm(parentName+"."+fieldName, childArrayJo, oldOutformulaParser, sysMetadataParser, mainModel);
			}else
			{
				try {
					String hiddenValueStr = fieldJo.getString("hidden_value");
					String toGetValue = null;
					if(StringUtil.isNotNull(hiddenValueStr)){
						if(hiddenValueStr.contains("$")){
							toGetValue = hiddenValueStr;
						}else{//需要用base64解码
							toGetValue = new String(Base64.decode(hiddenValueStr));
						}
					}
					Object value = oldOutformulaParser.parseValueScript(toGetValue);
					sysMetadataParser.setFieldValue(mainModel, parentName+"."+fieldName, value);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 递归解析JSONArray层级结构转化成JSONObject层级结构
	 * @param paseJson
	 * @param emptJson
	 * @author 严海星
	 * 2018年11月15日
	 */
	public static void recursionPaseArrayJsonToEmptJson(JSONArray paseJson,JSONObject emptJson)
	{
		for(int i = 0 ;i < paseJson.size(); i++)
		{
			JSONObject fieldJo = paseJson.getJSONObject(i);
			String fieldName = fieldJo.getString("name");
			String type = fieldJo.getString("type");
			if("array".equals(type))
			{
				JSONArray childArrayJo = fieldJo.getJSONArray("children");
				JSONArray childJo = new JSONArray();
				JSONObject childEmptJo = new JSONObject();
				recursionPaseArrayJsonToEmptJson(childArrayJo,childEmptJo);
				childJo.add(childEmptJo);
				emptJson.put(fieldName, childJo);
				
			}else if("object".equals(type))
			{
				JSONArray childArrayJo = fieldJo.getJSONArray("children");
				JSONObject childJo = new JSONObject();
				recursionPaseArrayJsonToEmptJson(childArrayJo,childJo);
				emptJson.put(fieldName, childJo);
			}else
			{
				String value = null;
				if("int".equals(type) || "long".equals(type) || "double".equals(type))
				{
					value = "1";
				}else if("boolean".equals(type))
				{
					value = "true";
				}
				emptJson.put(fieldName, value);
			}
			
		}
	}
	
	private static String getType(String type) {
		if (StringUtil.isNull(type)) {
			return "String";
		}
		if (type.equals("int")) {
			return "Integer";
		}
		return type;
	}

	private static Object changeValueType(Object value, String type,
			ISysMetadataParser sysMetadataParser) {
		if (value == null) {
			return null;
		}
		type = getType(type);
		try {
			if (value instanceof List) {
				List list = new ArrayList();
				for (Object o : (List) value) {
					list.add(sysMetadataParser.formatValue(o, type));
				}
				return list;
			}
			return sysMetadataParser.formatValue(value, type);
		} catch (Exception e) {
			e.printStackTrace();
			return value;
		}
	}

	/**
	 * 递归解析表单JSON转化为新公式定义器的modelJson
	 * @param formArrayJson
	 * @param modelJson
	 * @param formulaParser
	 * @author 严海星
	 * 2018年11月16日
	 */
	public static void recursionPaseFormArrayJsonTransModelJson(JSONArray formArrayJson, JSONObject modelJson,FormulaParser formulaParser)
	{
		ISysMetadataParser sysMetadataParser = (ISysMetadataParser)SpringBeanUtil.getBean("sysMetadataParser");
		
		for(int i = 0 ;i < formArrayJson.size(); i++)
		{
			JSONObject fieldJo = formArrayJson.getJSONObject(i);
			
			String fieldName = fieldJo.getString("name");
			String type = fieldJo.getString("type");
			if("arrayObject".equals(type) || "array".equals(type))
			{
				JSONArray childArrayJo = fieldJo.getJSONArray("children");
				JSONArray childJo = new JSONArray();
				//recursionPaseArrayJsonToEmptJson(childArrayJo,childEmptJo);
				//######################
				
				List<List<Object>> allValueList = new ArrayList<List<Object>>();
				List<String> colNames = new ArrayList<String>();
				for(int j = 0; j < childArrayJo.size() ; j++)
				{
					JSONObject colJo = childArrayJo.getJSONObject(j);
					String colName = colJo.getString("name");
					colNames.add(colName);
					//通过公式定义器解析获取一个列字段值的List集合
					List<Object>  colValueList = null;
					//兼容table中含有object的字段
					String fieldType = colJo.getString("type");
					if("object".equals(fieldType))//暂时考虑一级嵌套object
					{
						List<List<Object>> childFieldValues = new ArrayList<List<Object>>();
						List<String> childColName = new ArrayList<String>();
						JSONArray colJoChildArrayJo = colJo.getJSONArray("children");
						//Set<String> childFieldNames = colJo.keySet();
						for(int cc = 0 ; cc < colJoChildArrayJo.size(); cc++)
						{
							JSONObject childFieldJo = colJoChildArrayJo.getJSONObject(cc);
							String childFieldName = childFieldJo.getString("name");
							String childFieldtype = childFieldJo
									.getString("type");
							String hiddenValueStr = childFieldJo.getString("hidden_value");
							/*String toGetValue = null;
							if(StringUtil.isNotNull(hiddenValueStr)){
								if(hiddenValueStr.contains("$")){
									toGetValue = hiddenValueStr;
								}else{//需要用base64解码
									toGetValue = new String(Base64.decode(hiddenValueStr));
								}
							}*/
							Object formValue = formulaParser.parseValueScript(hiddenValueStr);
							formValue = changeValueType(formValue,
									childFieldtype, sysMetadataParser);
							if(formValue instanceof List)
							{
								childFieldValues.add((List<Object>)formValue);
							}else
							{
								List<Object> valueList = new ArrayList<Object>();
								valueList.add(formValue);
								childFieldValues.add(valueList);
							}
							//收集字段名称
							childColName.add(childFieldName);
						}
						
						//获取列表的行数  *由于表单数据中的table列数据存在单值情况,所以需要下方法获取整体的行数
						List<Integer> selectRowNum = new ArrayList<Integer>();
						for(List<Object> valueList : childFieldValues)
						{
							selectRowNum.add(valueList.size());
						}
						Collections.sort(selectRowNum);
						int rowNum  = selectRowNum.get(selectRowNum.size()-1);
						colValueList = new ArrayList<Object>();
						for(int c = 0 ; c < rowNum; c++)
						{
							JSONObject rowDataJo = new JSONObject();
							//列处理
							for(int k = 0 ; k < childFieldValues.size() ; k ++)
							{
								List<Object> colValues = childFieldValues.get(k);
								Object colValue = null;
								//预防表单数据中的table对象中列值只有一个值
								if(colValues.size()==1)
								{
									colValue = colValues.get(0);
								}else
								{
									colValue = colValues.get(c);
								}
								rowDataJo.put(childColName.get(k), colValue);
							}
							colValueList.add(rowDataJo);
						}
					//现只考虑二级明细表嵌套,且不考虑上级情况
					}else if ("arrayObject".equals(fieldType) || "array".equals(fieldType)){
						
						JSONArray colJoChildArrayJo = colJo.getJSONArray("children");
						List<List<Object>> childAllValueList = new ArrayList<List<Object>>();
						List<String> childColNames = new ArrayList<String>();
						for(int k = 0; k < colJoChildArrayJo.size() ; k++){
							JSONObject colJoChildJo = colJoChildArrayJo.getJSONObject(k);
							String hiddenValueStr = colJoChildJo.getString("hidden_value");
							String colJoChildJotype = colJoChildJo.getString("type");
							/*String toGetValue = null;
							if(StringUtil.isNotNull(hiddenValueStr)){
								if(hiddenValueStr.contains("$")){
									toGetValue = hiddenValueStr;
								}else{//需要用base64解码
									toGetValue = new String(Base64.decode(hiddenValueStr));
								}
							}*/
							Object formValue = formulaParser.parseValueScript(hiddenValueStr);
							formValue = changeValueType(formValue,colJoChildJotype, sysMetadataParser);
							if(formValue != null){
								if(formValue instanceof List){
									childAllValueList.add((List<Object>) formValue);
								}else{
									List<Object> list = new ArrayList<Object>();
									list.add(formValue);
									childAllValueList.add(list);
								}
								childColNames.add(colJoChildJo.getString("name"));
							}
						}
						
						if(childAllValueList.size() > 0){
							//获取列表的行数  *由于表单数据中的table列数据存在单值情况,所以需要下方法获取整体的行数
							List<Integer> selectRowNum = new ArrayList<Integer>();
							for(List<Object> valueList : childAllValueList)
							{
								selectRowNum.add(valueList.size());
							}
							Collections.sort(selectRowNum);
							int rowNum  = selectRowNum.get(selectRowNum.size()-1);
							
							/*for(List<Object> valueList : childAllValueList)
							{
								if(valueList.size() < rowNum && valueList.size() > 0){
									int a = rowNum - valueList.size();
									for(int n = 0 ; n < a ; n++){
										valueList.add(null);
									}
								}
							}*/
							
							List<Object> rowDataList = new ArrayList<Object>();
							
							for(int c = 0 ; c < rowNum; c++)
							{
								JSONObject rowDataJo = new JSONObject();
								//列处理
								for(int k = 0 ; k < childAllValueList.size() ; k ++)
								{
									List<Object> colValues = childAllValueList.get(k);
									Object colValue = null;
									if(colValues.size() > 0){
										//预防表单数据中的table对象中列值只有一个值
										if(colValues.size()==1){
											colValue = colValues.get(0);
										}else{
											colValue = colValues.get(c);
										}
										rowDataJo.put(childColNames.get(k), colValue);
									}
								}
								rowDataList.add(rowDataJo);
							}
							colValueList = new ArrayList<Object>();
							colValueList.add(rowDataList);
						}else{
							colValueList = new ArrayList<Object>();
						}
					}else{
						String hiddenValueStr = colJo.getString("hidden_value");
						String colJotype = colJo.getString("type");
						/*String toGetValue = null;
						if(StringUtil.isNotNull(hiddenValueStr)){
							if(hiddenValueStr.contains("$")){
								toGetValue = hiddenValueStr;
							}else{//需要用base64解码
								toGetValue = new String(Base64.decode(hiddenValueStr));
							}
						}*/
						Object formValue = formulaParser.parseValueScript(hiddenValueStr);
						formValue = changeValueType(formValue, colJotype,sysMetadataParser);
						if(formValue instanceof List)
						{
							colValueList = (List<Object>)formValue;
						}else
						{
							colValueList = new ArrayList<Object>();
							colValueList.add(formValue);
						}
					}
					allValueList.add(colValueList);
				}
				
				//获取列表的行数  *由于表单数据中的table列数据存在单值情况,所以需要下方法获取整体的行数
				List<Integer> selectRowNum = new ArrayList<Integer>();
				for(List<Object> valueList : allValueList)
				{
					selectRowNum.add(valueList.size());
				}
				Collections.sort(selectRowNum);
				int rowNum  = selectRowNum.get(selectRowNum.size()-1);
				
				for(int r = 0; r < rowNum; r++)
				{
					//列表的行数据json
					JSONObject rowJo = new JSONObject(true);
					for(int k = 0; k < allValueList.size(); k++)
					{
						List<Object> colValueList = allValueList.get(k);
						Object colValue = null;
						if(colValueList.size() > 0){
							//预防表单数据中的table对象中列值只有一个值
							if(colValueList.size()==1)
							{
								colValue = colValueList.get(0);
							}else
							{
								colValue = colValueList.get(r);
							}
							//行数据中的列数据设值
							rowJo.put(colNames.get(k), colValue);
						}
					}
					childJo.add(rowJo);
				}
				//#####################
				modelJson.put(fieldName, childJo);
				
			}else if("object".equals(type))
			{
				JSONArray childArrayJo = fieldJo.getJSONArray("children");
				JSONObject childJo = new JSONObject(true);
				recursionPaseFormArrayJsonTransModelJson(childArrayJo,childJo,formulaParser);
				modelJson.put(fieldName, childJo);
			}else
			{
				String hiddenValueStr = fieldJo.getString("hidden_value");
				/*String toGetValue = null;
				if(StringUtil.isNotNull(hiddenValueStr)){
					if(hiddenValueStr.contains("$")){
						toGetValue = hiddenValueStr;
					}else{//需要用base64解码
						toGetValue = new String(Base64.decode(hiddenValueStr));
					}
				}*/
				modelJson.put(fieldName,formulaParser.parseValueScript(hiddenValueStr,getType(type)));
			}
		}
	}
	

	/**
	 * 获取某个参数路径下，值的最大个数，从而决定构建Array的时候，成员有多少个
	 * 
	 * @param valuesMap
	 * @param path
	 * @return
	 */
	private static int getMaxLength(Map<String, List<String>> valuesMap,
			String path) {
		int length = 0;
		for (String key : valuesMap.keySet()) {
			if (!key.startsWith(path)) {
				continue;
			}
			int thisSize = valuesMap.get(key).size();
			if (thisSize > length) {
				length = thisSize;
			}
		}
		return length;
	}

	/**
	 * 判断参数列表中是不是存在某个参数
	 * 
	 * @param valuesMap
	 * @param path
	 * @return
	 */
	private static boolean isContainsPath(Map<String, List<String>> valuesMap,
			String path) {
		for (String key : valuesMap.keySet()) {
			if (key.startsWith(path)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 循环处理传入参数，对其中的列表类型参数进行处理
	 * 
	 * @param paseJson
	 * @param valuesMap
	 * @throws Exception
	 */
	public static Map<String, JSONArray> buildArrayFields(JSONArray paseJson,
			Map<String, List<String>> valuesMap) throws Exception {
		Map<String, JSONArray> arraysMap = new HashMap<String, JSONArray>();
		for (String key : valuesMap.keySet()) {
			List<String> values = valuesMap.get(key);
			if (values != null && values.size() > 1) {
				String arrayFieldPath = findNearestArray(paseJson, key);
				if (arraysMap.containsKey(arrayFieldPath)) {
					continue;
				}
				if (arrayFieldPath != null) {
					JSONObject arrayField = getFieldObj(paseJson,
							arrayFieldPath);
					String childType = arrayField.getString("type");
					JSONArray array = new JSONArray();
					//type为arrayObject时才有children
					if("arrayObject".equals(childType) || "array".equals(childType)){
						JSONArray childArrayJo = arrayField
								.getJSONArray("children");
						String path = arrayFieldPath;
						int length = getMaxLength(valuesMap, path);
						
						for (int i = 0; i < length; i++) {
							JSONObject o = new JSONObject();
							for (int j = 0; j < childArrayJo.size(); j++) {
								JSONObject fieldObj = childArrayJo.getJSONObject(j);
								String fieldName = fieldObj.getString("name");
								String fieldPath = path + "." + fieldName;
								if (!isContainsPath(valuesMap, fieldPath)) {
									continue;
								}
								String type = fieldObj.getString("type");
								if ("arrayObject".equals(type) || "array".equals(type)) {
									// 无需处理
								} else if (type.equals("object")) {
									o.put(fieldName, buildObjectField(fieldObj,
											valuesMap, i, fieldPath));
								} else {
									String fieldValue = getFieldValueInArray(
											valuesMap,
											fieldPath, i);
									if (fieldValue != null) {
										o.put(fieldName, fieldValue);
									}
								}
							}
							array.add(o);
						}
						arraysMap.put(arrayFieldPath, array);
					}else{//如果为arrayInt,arrayBoolean,arrayDouble,arrayString等单层数组的情况下
						//if(values != null && values.size() > 0){
							//arraysMap.put(arrayFieldPath, JSON.parseArray(JSON.toJSONString(values)));
							/*String realType = childType.replace("array", "").toLowerCase();
							if("int".equals(realType)){
								arraysMap.put(arrayFieldPath, array);
							}else if("boolean".equals(realType)){
								arraysMap.put(arrayFieldPath, array);
							}else if("double".equals(realType)){
								arraysMap.put(arrayFieldPath, array);
							}else if("string".equals(realType)){
								arraysMap.put(arrayFieldPath, JSON.parseArray(JSON.toJSONString(values)));
							}*/
						//}
					}
				}
			}
		}
		return arraysMap;
	}

	/**
	 * 构建object类型的参数数据
	 * 
	 * @param fieldObj
	 * @param valuesMap
	 * @param pos
	 * @param parentPath
	 * @return
	 */
	private static JSONObject buildObjectField(JSONObject fieldObj,
			Map<String, List<String>> valuesMap, int pos, String parentPath) {

		JSONObject o = new JSONObject();
		JSONArray children = fieldObj.getJSONArray("children");
		String fieldPath = parentPath;
		for (int i = 0; i < children.size(); i++) {
			JSONObject field_children = children.getJSONObject(i);
			String name = field_children.getString("name");
			fieldPath = parentPath + "." + name;
			if (!isContainsPath(valuesMap, fieldPath)) {
				continue;
			}
			String type = field_children.getString("type");
			if (type.equals("array")) {
				// 无需处理
			} else if (type.equals("object")) {
				o.put(name, buildObjectField(field_children, valuesMap, pos,
						fieldPath));
			} else {
				String fieldValue = getFieldValueInArray(
						valuesMap,
						fieldPath, pos);
				if (fieldValue != null) {
					o.put(name, fieldValue);
				}
			}
		}

		return o;
	}
	
	/**
	 * 查找最近的数组参数
	 * 
	 * @param paseJson
	 * @param path
	 * @return
	 * @throws Exception
	 */
	private static String findNearestArray(JSONArray paseJson, String path)
			throws Exception {
		String arrayFieldPath = null;
		String[] fields = path.split("\\.");
		JSONObject field_json = getJsonObjectByName(paseJson, fields[0]);
		String type = field_json.getString("type");
		//if ("array".equals(type)) {
		if ("arrayObject".equals(type) || "array".equals(type)) {
			arrayFieldPath = fields[0];
		}
		String parentName = fields[0];
		for (int i = 1; i < fields.length; i++) {
			JSONArray children = field_json.getJSONArray("children");
			field_json = getJsonObjectByName(children, fields[i]);
			if (field_json == null) {
				throw new Exception("传入参数列表中找不到字段【" + getFieldNameStr(fields, i)
						+ "】,参数列表：" + paseJson);
			}
			parentName = parentName + "." + fields[i];
			type = field_json.getString("type");
			//if ("array".equals(type)) {
			if (type.startsWith("array")) {
				arrayFieldPath = parentName;
			}
		}
		return arrayFieldPath;
	}

	@Deprecated
	private static boolean checkChildrenLastLevel(JSONArray childArrayJo) {
		for (int i = 0; i < childArrayJo.size(); i++) {
			JSONObject fieldJo = childArrayJo.getJSONObject(i);
			String type = fieldJo.getString("type");
			if (type.equals("object") || type.equals("array")) {
				return false;
			}
		}
		return true;
	}

	@Deprecated
	private static void buildArrayChildren(String parentName,
			JSONArray childArrayJo, Map<String, List<String>> valuesMap,
			JSONArray childJo) {
		String path = parentName.substring(1);
		path = path.substring(0, path.lastIndexOf("."));
		int length = 0;
		Map<String, List<String>> childrenValues = new HashMap<String, List<String>>();
		for (int j = 0; j < childArrayJo.size(); j++) {
			JSONObject field = childArrayJo.getJSONObject(j);
			String fieldName_child = field.getString("name");
			List<String> values_children = valuesMap
					.get(path + "." + fieldName_child);
			if (values_children != null
					&& !values_children.isEmpty()) {
				childrenValues.put(fieldName_child,
						values_children);
				if (values_children.size() > length) {
					length = values_children.size();
				}
			}
		}
		for (int j = 0; j < length; j++) {
			JSONObject o = new JSONObject();
			for (String name : childrenValues.keySet()) {
				String fieldValue = getFieldValueInArray(
						childrenValues,
						name, j);
				if (fieldValue != null) {
					o.put(name, fieldValue);
				}
			}
			childJo.add(o);
		}
	}

	/**
	 * 构建传入参数
	 * 
	 * @param paseJson
	 *            参数结构
	 * @param result
	 *            转换后的结果数据
	 * @param valuesMap
	 *            表单传过来的数据
	 * @param feilds
	 * @throws Exception
	 */
	public static void recursionPaseArrayJsonToJson(String parentName,
			JSONArray paseJson,
			JSONObject result, Map<String, List<String>> valuesMap,
			Set<String> fields, Map<String, JSONArray> arraysMap)
			throws Exception {

		String fieldPath = "";
		for (int i = 0; i < paseJson.size(); i++) {
			JSONObject fieldJo = paseJson.getJSONObject(i);
			String fieldName = fieldJo.getString("name");
			fieldPath = parentName + "." + fieldName;
			if (fieldPath.startsWith(".")) {
				fieldPath = fieldPath.substring(1);
			}
			if (!fields.contains(fieldPath)) {
				continue;
			}
			String type = fieldJo.getString("type");
			if ("arrayObject".equals(type) || "array".equals(type)) {
				JSONArray childArray = arraysMap.get(fieldPath);
				if (childArray != null) {
					// 直接处理，不继续往下处理
					result.put(fieldName, childArray);
				}else{
					JSONArray childArrayJo = fieldJo.getJSONArray("children");
					JSONArray childJo = new JSONArray();
					JSONObject childEmptJo = new JSONObject();
					recursionPaseArrayJsonToJson(fieldPath, childArrayJo,
							childEmptJo,
							valuesMap, fields, arraysMap);
					childJo.add(childEmptJo);
					result.put(fieldName, childJo);
				}

			} else if ("object".equals(type)) {
				JSONArray childArrayJo = fieldJo.getJSONArray("children");
				JSONObject childJo = new JSONObject();
				recursionPaseArrayJsonToJson(fieldPath, childArrayJo, childJo,
						valuesMap,
						fields, arraysMap);
				result.put(fieldName, childJo);
			} else {
				//arrayInt,arrayDouble,arrayString,arrayBoolean
				if(type.startsWith("array")){
					List<String> values = valuesMap.get(fieldPath);
					if(values != null && values.size() > 0){
						if("arrayInt".equals(type)){
							List<Integer> realList = new ArrayList<Integer>();
							for(String s : values){
								realList.add(Integer.valueOf(s));
							}
							result.put(fieldName, realList);
						}else if("arrayDouble".equals(type)){
							List<Double> realList = new ArrayList<Double>();
							for(String s : values){
								realList.add(Double.valueOf(s));
							}
							result.put(fieldName, realList);
						}else if("arrayBoolean".equals(type)){
							List<Boolean> realList = new ArrayList<Boolean>();
							for(String s : values){
								realList.add(Boolean.valueOf(s));
							}
							result.put(fieldName, realList);
						}else if("arrayString".equals(type)){
							result.put(fieldName, values);
						}
					}
				}else{
					List<String> values = valuesMap.get(fieldPath);
					Object value = "";
					if (values == null || values.isEmpty()) {
						if ("int".equals(type) || "long".equals(type)
								|| "double".equals(type)) {
							value = "1";
						} else if ("boolean".equals(type)) {
							value = "true";
						}
						result.put(fieldName, value);
					} else if (values.size() == 1) {
						if ("int".equals(type) || "long".equals(type)
								|| "float".equals(type)
								|| "double".equals(type)) {
							value = Double.valueOf(values.get(0));
						} else if ("boolean".equals(type)) {
							value = Boolean.valueOf(values.get(0));
						} else {
							value = values.get(0);
						}
						result.put(fieldName, value);
					} else {
						if ("int".equals(type) || "long".equals(type)
								|| "float".equals(type)
								|| "double".equals(type)) {
							value = Double.valueOf(values.get(0));
						} else if ("boolean".equals(type)) {
							value = Boolean.valueOf(values.get(0));
						} else {
							value = values.get(0);
						}
						result.put(fieldName, value);
					}
				}
			}
		}
	}

	private static String getFieldValueInArray(
			Map<String, List<String>> childrenValues, String fieldName,
			int pos) {
		List<String> list = childrenValues.get(fieldName);
		if (list == null || list.size() == 0) {
			return null;
		}
		if (list.size() < pos + 1) {
			return list.get(0);
		}
		return list.get(pos);
	}

	public static Object getFieldValue(JSONObject obj, String path)
			throws Exception {
		String[] fields = path.split("\\.");
		Object result = "";
		for (int i = 0; i < fields.length - 1; i++) {
			result = obj.get(fields[i]);
			if (result instanceof JSONObject) {
				obj = (JSONObject) result;
			} else if (result instanceof JSONArray) {
				obj = ((JSONArray) result).getJSONObject(0);
			}
		}

		return obj.getJSONArray(fields[fields.length - 1]);
	}

	public static JSONObject getFieldObj(JSONArray array_in, String path)
			throws Exception {
		String[] fields = path.split("\\.");
		JSONObject field_json = getJsonObjectByName(array_in, fields[0]);

		for (int i = 1; i < fields.length; i++) {
			JSONArray children = field_json.getJSONArray("children");
			field_json = getJsonObjectByName(children, fields[i]);
			if (field_json == null) {
				throw new Exception("传入参数列表中找不到字段【" + getFieldNameStr(fields, i)
						+ "】,参数列表：" + array_in);
			}
		}
		return field_json;
	}

	public static String getFieldType(JSONArray array_in, String path)
			throws Exception {
		return ((JSONObject) getFieldObj(array_in, path)).getString("type");
	}

	public static JSONObject getJsonObjectByName(JSONArray array, String name) {
		for (Object o : array) {
			JSONObject field = (JSONObject) o;
			if (name.equals(field.get("name"))) {
				return field;
			}
		}
		return null;
	}

	public static String getFieldNameStr(String[] fields, int pos) {
		String result = "";
		for (int i = 0; i <= pos; i++) {
			result += fields[i] + ".";
		}
		if (result.length() > 0) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	public static String frommatSql(String sql) {
		// select * from sys_org_element where 1=1 and fd_name like '%{fdname}%'
		List<String> conditions = new ArrayList<String>();
		StringBuffer sbreplace = new StringBuffer();
		Pattern p = Pattern.compile("\\[[^\\[\\]]+\\]");
		Matcher m = p.matcher(sql);
		while (m.find()) {
			String condition = m.group();
			if (!(condition.contains("startIndex"))) {
				conditions.add(condition);
			}
			m.appendReplacement(sbreplace, " ");
		}
		for (String condition : conditions) {
			if (condition.contains("like")) {
				int a = condition.indexOf("{");
				int b = condition.indexOf("}");
				String fieldName = condition.substring(a, b + 1);
				String fieldName1 = fieldName.substring(1,
						fieldName.length() - 1);
				// 模糊查询语句
				// fieldName =
				// 计算%的出现的个数
				int times = 0;
				String[] splitArray = condition.split("%");
				// if(times.length)
				for (String s : splitArray) {
					if (StringUtil.isNotNull(s))
						times++;
				}
				times--;
				if (times >= 2) {
					// 需要判断%是否在参数的两边还是都是在其中一边
					int index1 = condition.indexOf("%");
					int index2 = condition.indexOf("{");
					int index3 = condition.indexOf("}");
					int index4 = condition.lastIndexOf("%");
					if (index2 > index1 && index3 < index4) {// %在参数两边
						condition = condition.replaceAll(
								"%(.*?(" + fieldName1 + ").*?)%",
								"%" + fieldName + "%");
					} else if (index2 > index1 && index2 > index4) {// %都在左边
						String subStr = condition.substring(index4, index3 + 1);
						condition = condition.replace(subStr,
								"%" + fieldName + "'");
					} else if (index3 < index1 && index3 < index4) {// %都在右边
						String subStr = condition.substring(index2, index1);
						condition = condition.replace(subStr,
								"'" + fieldName + "");
					}
				} else if (times == 1) {// 需要判断%在哪个位置
					// 判断%在哪边
					int index = condition.indexOf("%");
					String subStr = condition.substring(index,
							condition.length());
					if (subStr.contains(fieldName)) {// %在左边
						int index2 = condition.indexOf("}");
						String subStr2 = condition.substring(index, index2 + 1);
						condition = condition.replace(subStr2,
								"%" + fieldName + "'");
					} else {// %在右边
						int index2 = condition.indexOf("{");
						String subStr2 = condition.substring(index2, index);
						condition = condition.replace(subStr2,
								"'" + fieldName + "");
					}
				}
			}
			sbreplace.append(condition.substring(1, condition.length() - 1));
		}

		sql = m.appendTail(sbreplace).toString();
		return sql;
	}
	
	public static void main(String[] args) throws Exception {
		/*String paseXMLTransParamInJson = paseXMLTransParamInJson("<xxx abc='avv' ccc='eee'><a>a</a><b>b</b></xxx>");
		System.out.println(paseXMLTransParamInJson);*/
		String s ="<?xml version=\"1.0\" encoding=\"UTF-8\"?><ufinterface sender=\"ekp\" receiver=\"u8\" roottag=\"pay\" proc=\"Add\"><pay><header><vouchtype>49</vouchtype><vouchcode>0000000008</vouchcode><vouchdate>2016-12-07</vouchdate><period>12</period><customercode>gys001</customercode><departmentcode>1</departmentcode><personcode /><itemclasscode>98</itemclasscode><itemcode>02</itemcode><itemname>收到的税费返还</itemname><orderid /><balancecode>1</balancecode><notecode /><digest /><oppositebankcode>6225768710500110</oppositebankcode><foreigncurrency>人民币</foreigncurrency><currencyrate>1</currencyrate><amount>711</amount><originalamount>711</originalamount><operator>yue</operator><balanceitemcode /><flag>AP</flag><sitemcode /><oppositebankname>招商银行</oppositebankname><bankname /><bankaccount /><ccontracttype /><ccontractid /><iamount_s>0</iamount_s><startflag>0</startflag></header><body><entry><mainid>1000000001</mainid><type>0</type><customercode>gys001</customercode><originalamount>500</originalamount><amount>500</amount><itemcode /><projectclass /><project /><departmentcode>1</departmentcode><personcode>01</personcode><orderid /><itemname /><ccontype /><cconid /><iamt_s>0</iamt_s><iramt_s>0</iramt_s></entry><entry><mainid>1000000001</mainid><type>0</type><customercode>gys001</customercode><originalamount>11</originalamount><amount>11</amount><itemcode /><projectclass /><project /><departmentcode>1</departmentcode><personcode /><orderid /><itemname /><ccontype /><cconid /><iamt_s>0</iamt_s><iramt_s>0</iramt_s></entry><entry><mainid>1000000001</mainid><type>0</type><customercode>gys001</customercode><originalamount>200</originalamount><amount>200</amount><itemcode /><projectclass /><project /><departmentcode>1</departmentcode><personcode /><orderid /><itemname /><ccontype /><cconid /><iamt_s>0</iamt_s><iramt_s>0</iramt_s></entry></body></pay></ufinterface>";
		Document document = DocumentHelper.parseText(s);
		JSONObject inParam = new JSONObject(true);
		recursionPaseXmlToJSON(document.getRootElement(),inParam);
		System.out.println(inParam.toJSONString());
		
		System.out.println(paseJsonCreateXmlString(inParam));
	}

	/**
	 * 解析JSON生成xml格式字符串,该方法用于rest函数需要传输xml作为body时使用
	 * @param jo
	 * @return
	 * @author 严海星
	 * 2019年3月21日
	 */
	public static String paseJsonCreateXmlString(JSONObject jo) {
		Document document = DocumentHelper.createDocument();
		/*Element ee1 = DocumentHelper.createElement("top");
		document.add(ee1);
		Element e = document.getRootElement();
		Element ee = DocumentHelper.createElement("doc");
		e.add(ee);
		Element eee = DocumentHelper.createElement("test");
		eee.setText("xxxxxxxxxxxx");
		ee.add(eee);
		System.out.println(document.asXML());
		return null;*/
		Set<String> keySet = jo.keySet();
		if(keySet.size()==1){//只能有一个顶级元素,否则不解析
			String topName = null;
			for(String topEleName : keySet){
				topName = topEleName;
			}
			Element topEle = DocumentHelper.createElement(topName);
			document.add(topEle);
			Element rootElement = document.getRootElement();
			Object childObj = jo.get(topName);
			//JSONObject childObj = jo.getJSONObject(topName);
			recursionPaseJsonToCreateXml(rootElement,childObj);
		}
		return document.asXML();
	}
	
	/**
	 * 递归解析Object生成xml
	 * @param parentEle
	 * @param obj
	 * @author 严海星
	 * 2019年3月22日
	 */
	private static void recursionPaseJsonToCreateXml(Element parentEle, Object obj){
		if(obj instanceof JSONObject){
			JSONObject jo = (JSONObject) obj;
			Set<String> keySet = jo.keySet();
			for(String name : keySet){
				if(name.startsWith(XML_2_JSON_ATTR_FLAG)){//xml标签属性
					parentEle.addAttribute(name.replaceFirst(XML_2_JSON_ATTR_FLAG, ""), jo.getString(name));
				}else{
					Element childEle = DocumentHelper.createElement(name);
					Object childObj = jo.get(name);
					parentEle.add(childEle);
					recursionPaseJsonToCreateXml(childEle,childObj);
				}
			}
		}else if(obj instanceof JSONArray){
			Element parentparent = parentEle.getParent();
			String parentName = parentEle.getName();
			parentparent.remove(parentEle);
			JSONArray ja = (JSONArray) obj;
			for(int i = 0 ; i < ja.size() ; i++){
				Element childEle = DocumentHelper.createElement(parentName);
				parentparent.add(childEle);
				Object childObj = ja.get(i);
				recursionPaseJsonToCreateXml(childEle,childObj);
			}
		}else{
			parentEle.setText(obj.toString());
		}
	}
	
	/*private static void recursionPaseJsonToCreateXml(Element parentEle, JSONObject jo) {
		Set<String> keySet = jo.keySet();
		for(String name : keySet){
			Object childObj = jo.get(name);
			if(childObj instanceof JSONObject){
				JSONObject childJo = (JSONObject) childObj;
				Element childEle = DocumentHelper.createElement(name);
				recursionPaseJsonToCreateXml(childEle,childJo);
				parentEle.add(childEle);
			}else if(childObj instanceof JSONArray){
				JSONArray childArray = (JSONArray) childObj;
				for(int i = 0 ; i < childArray.size() ; i ++){
					Object obj = childArray.get(i);
					Element ele = DocumentHelper.createElement(name);
					if(obj instanceof JSONObject){
						JSONObject childJo = (JSONObject) obj;
						recursionPaseJsonToCreateXml(ele,childJo);
						parentEle.add(ele);
					}else if(obj instanceof JSONArray){
						
					}else{//单级数组
						ele.setText(obj.toString());
						parentEle.add(ele);
					}
				}
			}else{
				Element childEle = DocumentHelper.createElement(name);
				childEle.setText(childObj.toString());
				parentEle.add(childEle);
			}
		}
	}*/
	/*public static void main(String[] args) {
		String json = "{\"pk_org\":\"T3005\",\"pk_group\":\"T3\",\"trade_type\":\"D4\",\"bill_date\":\"2019-04-09\",\"source_flag\":\"0\",\"testarray\":[\"11\",\"333\"],\"testobjarray\":[{\"aa\":\"11\"},{\"aa\":\"22\"}],\"approver\":\"TH300510091\",\"approve_date\":\"2019-04-09\",\"testobj\":{\"teobj\":\"xxx\"},\"billmaker\":\"TH300510091\",\"billmaker_date\":\"2019-04-09\",\"primal_money\":\"123450.00\",\"local_money\":\"123450.00\",\"memo\":\"盈利收款\",\"bill_type\":\"F4\"}";
		JSONObject jo = JSON.parseObject(json);
		Set<String> keySet = jo.keySet();
		for(String key : keySet){
			System.out.println(key + "------" + jo.getString(key));
		}
		//jo.get
		LinkedHashMap<String, Object> jsonMap = JSON.parseObject(json, new TypeReference<LinkedHashMap<String, Object>>() {});
		JSONObject newjo = new JSONObject(true);
        for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
        	if(entry.getValue() instanceof JSONArray){
        		System.out.print("is array ");
        	}else if(entry.getValue() instanceof JSONObject){
        		System.out.print("is object ");
        	}else if(entry.getValue() instanceof String){
        		System.out.print("is string ");
        	}
            System.out.println(entry.getKey() + ":" + entry.getValue());
            newjo.put(entry.getKey(), entry.getValue());
        }
        JSONArray ja = new JSONArray();
        System.out.println(JSON.toJSONStringZ(newjo, SerializeConfig.getGlobalInstance(), SerializerFeature.QuoteFieldNames));
        JSONObject jj = JSON.parseObject(JSON.toJSONStringZ(newjo, SerializeConfig.getGlobalInstance(), SerializerFeature.QuoteFieldNames));
        JSONObject j1 = JSON.parseObject(json);
        System.out.println(jj.toJSONString());
        System.out.println(j1.toJSONString());
	}*/
}
