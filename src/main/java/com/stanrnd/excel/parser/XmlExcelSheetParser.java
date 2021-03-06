package com.stanrnd.excel.parser;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.stanrnd.excel.exception.ExcelParserException;
import com.stanrnd.excel.meta.Color;
import com.stanrnd.excel.meta.ExcelColumn;
import com.stanrnd.excel.meta.ExcelData;
import com.stanrnd.excel.meta.ExcelHeader;
import com.stanrnd.excel.meta.ExcelSheet;
import com.stanrnd.excel.meta.ExcelStyle;
import com.stanrnd.excel.meta.ExcelTitle;
import com.stanrnd.excel.meta.FillPattern;
import com.stanrnd.excel.meta.FontSize;
import com.stanrnd.excel.parser.bean.FieldValueParser;
import com.stanrnd.excel.parser.bean.MethodValueParser;
import com.stanrnd.excel.parser.bean.ValueParser;

/**
 * 
 * @author stalin.jeyaraj
 *
 */
public class XmlExcelSheetParser {

	public static Map<Class<?>, ExcelSheet> parse(InputStream inputStream) throws ExcelParserException {
		try {
			Map<Class<?>, ExcelSheet> excelSheets = new LinkedHashMap<>();
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document doc = builder.parse(inputStream);
			doc.getDocumentElement().normalize();
			NodeList sheets = doc.getDocumentElement().getChildNodes();
			for (int i = 0; i < sheets.getLength(); i++) {
				Node sheet = sheets.item(i);
				if(sheet instanceof Element) {
					Element sheetElement = (Element) sheet;
					Class<?> clazz = Class.forName(sheetElement.getAttribute("class"));
					excelSheets.put(clazz, parseSheet(sheetElement, clazz));
				}
			}
			return excelSheets;
		} catch (ExcelParserException e) {
			throw e;
		} catch (Exception e) {
			throw new ExcelParserException(e);
		}
	}

	private static ExcelSheet parseSheet(Element excel, Class<?> clazz) throws ExcelParserException {
		Map<String, ValueParser> valueParsers = parseValueParsers(clazz);
		ExcelSheet excelSheet = new ExcelSheet(excel.getAttribute("name"));
		NodeList sheetChilds = excel.getChildNodes();
		for (int j = 0; j < sheetChilds.getLength(); j++) {
			Node sheetChild = sheetChilds.item(j);
			if ("TITLE".equalsIgnoreCase(sheetChild.getNodeName())) {
				excelSheet.setTitle(parseTitle((Element) sheetChild));
			} else if ("COLUMNS".equalsIgnoreCase(sheetChild.getNodeName())) {
				List<ExcelColumn> excelColumns = new ArrayList<>();
				NodeList columns = sheetChild.getChildNodes();
				for (int l = 0; l < columns.getLength(); l++) {
					Node column = columns.item(l);
					if(column instanceof Element) {
						excelColumns.add(parseExcelColumn(clazz, (Element) column, valueParsers));
					}
				}
				excelSheet.setColumns(excelColumns);
			}
		}
		return excelSheet;
	}
	
	private static Map<String, ValueParser> parseValueParsers(Class<?> clazz) {
		if(clazz != null) {
			Map<String, ValueParser> map = parseValueParsers(clazz.getSuperclass());
			for(Field field:clazz.getDeclaredFields()) {
				map.put(field.getName().toUpperCase(), new FieldValueParser(field));
			}
			for(Method method:clazz.getDeclaredMethods()) {
				map.put(method.getName().toUpperCase(), new MethodValueParser(method));
			}
			return map;
		}
		return new HashMap<>();
	}
	
	private static ExcelTitle parseTitle(Element title) {
		ExcelTitle excelTitle = new ExcelTitle();
		
		parseExcelStyle(title, excelTitle);
		
		if(title.hasAttribute("text")) {
			excelTitle.setText(title.getAttribute("text"));
		}
		
		return excelTitle;
	}
	
	private static ExcelColumn parseExcelColumn(Class<?> clazz, Element column, Map<String, ValueParser> valueParsers) throws ExcelParserException {
		ExcelColumn excelColumn = new ExcelColumn();
		if(!column.hasAttribute("field-name") && !column.hasAttribute("method-name")) {
			throw new ExcelParserException("Attribute either 'field-name' or 'method-name' mandatory.");
		}
		if(column.hasAttribute("field-name")) {
			if(!valueParsers.containsKey(column.getAttribute("field-name").toUpperCase())) {
				throw new ExcelParserException("Field '" + column.getAttribute("field-name") + "' not found in the bean '" + clazz.getName() + "'.");
			}
			excelColumn.setValueParser(valueParsers.get(column.getAttribute("field-name").toUpperCase()));
		}
		if(column.hasAttribute("method-name")) {
			if(!valueParsers.containsKey("GET" + column.getAttribute("method-name").toUpperCase())) {
				throw new ExcelParserException("Method '" + column.getAttribute("method-name") + "' not found in the bean '" + clazz.getName() + "'.");
			}
			excelColumn.setValueParser(valueParsers.get("GET" + column.getAttribute("method-name").toUpperCase()));
		}
		if(column.hasAttribute("width")) {
			excelColumn.setWidth(Integer.parseInt(column.getAttribute("width")));
		}
		if(column.hasAttribute("order")) {
			excelColumn.setOrder(Integer.parseInt(column.getAttribute("order")));
		}
		NodeList columnChilds = column.getChildNodes();
		for (int l = 0; l < columnChilds.getLength(); l++) {
			Node columnChild = columnChilds.item(l);
			if(columnChild instanceof Element) {
				if ("HEADER".equalsIgnoreCase(columnChild.getNodeName())) {
					excelColumn.setHeader(parseExcelHeader((Element) columnChild));
				} else if ("DATA".equalsIgnoreCase(columnChild.getNodeName())) {
					excelColumn.setData(parseExcelData((Element) columnChild));
				}
			}
		}
		
		if(excelColumn.getHeader() == null) {
			ExcelHeader excelHeader = new ExcelHeader();
			if(column.hasAttribute("field-name")) {
				excelHeader.setText(column.getAttribute("field-name"));
			}
			if(column.hasAttribute("method-name")) {
				excelHeader.setText(column.getAttribute("method-name"));
			}
			
			excelColumn.setHeader(excelHeader);
		}
		
		if(excelColumn.getData() == null) {
			ExcelData excelData = new ExcelData();
			
			excelColumn.setData(excelData);
		}
		
		return excelColumn;
	}
	
	private static ExcelData parseExcelData(Element data) {
		ExcelData excelData = new ExcelData();
		
		parseExcelStyle(data, excelData);
		
		return excelData;
	}
	
	private static ExcelHeader parseExcelHeader(Element header) {
		ExcelHeader excelHeader = new ExcelHeader();
		
		parseExcelStyle(header, excelHeader);
		
		if(header.hasAttribute("text")) {
			excelHeader.setText(header.getAttribute("text"));
		}
		
		return excelHeader;
	}
	
	private static void parseExcelStyle(Element title, ExcelStyle excelStyle) {
		if(title.hasAttribute("height")) {
			excelStyle.setHeight(Short.parseShort(title.getAttribute("height")));
		}
		
		if(title.hasAttribute("fill-pattern")) {
			excelStyle.setFillPattern(FillPattern.valueOf(title.getAttribute("fill-pattern").toUpperCase()));
		}
		
		if(title.hasAttribute("fill-foreground")) {
			excelStyle.setFillForeground(Color.valueOf(title.getAttribute("fill-foreground").toUpperCase()));
		}
		
		if(title.hasAttribute("fill-background")) {
			excelStyle.setFillBackground(Color.valueOf(title.getAttribute("fill-background").toUpperCase()));
		}
		
		if(title.hasAttribute("font-name")) {
			excelStyle.setFontName(title.getAttribute("font-name"));
		}
		
		if(title.hasAttribute("font-size")) {
			excelStyle.setFontSize(FontSize.find(Short.parseShort(title.getAttribute("font-size"))));
		}
		
		if(title.hasAttribute("font-color")) {
			excelStyle.setFontColor(Color.valueOf(title.getAttribute("font-color").toUpperCase()));
		}
		
		if(title.hasAttribute("italic")) {
			excelStyle.setItalic(Boolean.parseBoolean(title.getAttribute("italic")));
		}
		
		if(title.hasAttribute("bold")) {
			excelStyle.setBold(Boolean.parseBoolean(title.getAttribute("bold")));
		}
		
		if(title.hasAttribute("underline")) {
			excelStyle.setUnderline(Byte.parseByte(title.getAttribute("underline")));
		}
	}

}
