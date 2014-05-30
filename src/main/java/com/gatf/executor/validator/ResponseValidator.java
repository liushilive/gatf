package com.gatf.executor.validator;

/*
Copyright 2013-2014, Sumeet Chhetri

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler.ResponseType;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestFailureReason;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.ning.http.client.Response;

/**
 * @author Sumeet Chhetri
 * Defines contract for response level node validations after test case execution
 */
public abstract class ResponseValidator {

	protected abstract Object getInternalObject(Response response) throws Exception;
	protected abstract ResponseType getType();
	protected abstract String getNodeValue(Object intObj, String node) throws Exception;
	
	public boolean hasValidationFunction(String node) {
		return node!=null && node.endsWith("]") && (node.startsWith("#providerValidation[") 
				|| node.startsWith("#liveProviderValidation["));
	}
	
	public void doNodeLevelValidation(String lhs, String lhsv, String oper, String rhsv, 
			AcceptanceTestContext context, TestCase testCase) {
		if(hasValidationFunction(lhs)) 
		{
			boolean isLiveProvider = lhs.startsWith("#liveProviderValidation[");
			String path = lhs.substring(lhs.indexOf("[")+1, lhs.length()-1);
			String[] pathelements = path.split("\\.");
			if(pathelements.length<3)
			{
				throw new AssertionError("Invalid nomenclature for provider validation node, can have only 3 elements) - " + lhs);
			}
			List<Map<String, String>> provData = null;
			if(isLiveProvider)
				provData = context.getLiveProviderData(pathelements[0], testCase);
			else
				provData = context.getProviderTestDataMap().get(pathelements[0]);
			Assert.assertNotNull("Provider not found - " + pathelements[0], provData);
			int index = -1;
			try {
				index = Integer.parseInt(pathelements[1]);
			} catch (Exception e) {
				Assert.assertNotNull("Provider index has to be a number - " + pathelements[1], null);
			}
			Assert.assertTrue("Specified index is outside provider data range - "+pathelements[1], 
					index>=0 && index<provData.size());
			Map<String, String> row = provData.get(index);
			Assert.assertNotNull("Provider row not found - " + index, row);
			Assert.assertTrue("Specified provider property not found - "+pathelements[2], row.containsKey(pathelements[2]));
			lhsv = row.get(pathelements[2]);
		}
		compare(lhsv, oper, rhsv, lhs);
	}
	
	private void compare(String lhsv, String oper, String rhsv, String lhs)
	{
		if(oper.equals("=")) {
			Assert.assertEquals("Node validation failed for " + lhs, lhsv, rhsv);
		} else if(oper.equals("<")) {
			Assert.assertTrue("Node validation failed for " + lhs, lhsv.compareTo(rhsv)==-1);
		} else if(oper.equals(">")) {
			Assert.assertTrue("Node validation failed for " + lhs, lhsv.compareTo(rhsv)==1);
		} else if(oper.equals("<=")) {
			Assert.assertTrue("Node validation failed for " + lhs, lhsv.compareTo(rhsv)==-1 || lhsv.compareTo(rhsv)==0);
		} else if(oper.equals(">=")) {
			Assert.assertTrue("Node validation failed for " + lhs, lhsv.compareTo(rhsv)==1 || lhsv.compareTo(rhsv)==0);
		} else if(oper.equals("!=")) {
			Assert.assertNotEquals("Node validation failed for " + lhs, lhsv, rhsv);
		} else if(oper.equals("regex")) {
			Assert.assertTrue("Regex validation failed for " + lhs, lhsv.matches(rhsv));
		} else if(oper.equals("startswith")) {
			Assert.assertTrue("Node validation failed for " + lhs, lhsv.startsWith(rhsv));
		} else if(oper.equals("endswith")) {
			Assert.assertTrue("Node validation failed for " + lhs, lhsv.endsWith(rhsv));
		} else if(oper.equals("contains")) {
			Assert.assertTrue("Node validation failed for " + lhs, lhsv.contains(rhsv));
		}
	}
	
	public List<String> getNodeProperties(String node)
	{
		List<String> nodeProps = new ArrayList<String>();
		if(node.indexOf(",")!=-1)
		{
			nodeProps.add(node.substring(0, node.indexOf(",")));//The attributeName or validation function
			node = node.substring(node.indexOf(",")+1);
			if(node.indexOf(",")!=-1)
			{
				nodeProps.add(node.substring(0, node.indexOf(",")));//The value to compare or the comprarison function
				node = node.substring(node.indexOf(",")+1);
				if(node.indexOf(",")!=-1)
				{
					nodeProps.add(node.substring(0, node.indexOf(",")));//The value to compare
					node = node.substring(node.indexOf(",")+1);
				}
				else
					nodeProps.add(node);//The value to compare
			}
			else
				nodeProps.add(node);//The value to compare
		}
		else
		{
			nodeProps.add(node);//The attributeName
		}
		
		Assert.assertTrue("Invalid node properties specified", nodeProps.size()<=3);
		if(nodeProps.size()==3)
		{
			Assert.assertTrue("Invalid comparison operator specified, only one of (<, >, <=, >= , =, !=, " +
					"regex, startswith, endswith, contains) allowed", isValidOperator(nodeProps.get(1)));
		}
		
		return nodeProps;
	}
	
	private boolean isValidOperator(String oper)
	{
		return oper.equals("<") || oper.equals(">") || oper.equals("<=") || oper.equals(">=") || oper.equals("=") ||
				 oper.equals("!=") || oper.equals("regex") || oper.equals("startswith") || oper.equals("endswith") || 
				 oper.equals("contains");
	}
	
	public static String getXMLNodeValue(Node node)
	{
		String xmlValue = null;
		if(node.getNodeType()==Node.TEXT_NODE 
				|| node.getNodeType()==Node.CDATA_SECTION_NODE)
		{
			xmlValue = node.getNodeValue();
		}
		else if(node.getNodeType()==Node.ATTRIBUTE_NODE)
		{
			xmlValue = ((Attr)node).getValue();
		}
		else if(node.getNodeType()==Node.ELEMENT_NODE
				&& node.getChildNodes().getLength()>=1
				&& (node.getFirstChild().getNodeType()==Node.TEXT_NODE
				|| node.getFirstChild().getNodeType()==Node.CDATA_SECTION_NODE))
		{
			xmlValue = node.getFirstChild().getNodeValue();
		}
		return xmlValue;
	}
	
	public NodeList getNodeByXpath(String xpathStr, Document xmlDocument, String messagePrefix) throws XPathExpressionException
	{
		if(messagePrefix==null)
		{
			messagePrefix = "Expected Node ";
		}
		String oxpathStr = xpathStr;
		xpathStr = xpathStr.replaceAll("\\.", "\\/");
		if(xpathStr.charAt(0)!='/')
			xpathStr = "/" + xpathStr;
		XPath xPath =  XPathFactory.newInstance().newXPath();
		NodeList xmlNodeList = (NodeList) xPath.compile(xpathStr).evaluate(xmlDocument, XPathConstants.NODESET);
		Assert.assertTrue(messagePrefix + oxpathStr + " is null", xmlNodeList!=null && xmlNodeList.getLength()>0);
		return xmlNodeList;
	}
	
	public void validate(Response response, TestCase testCase, TestCaseReport testCaseReport, AcceptanceTestContext context)
	{
		try
		{
			Object intObj = getInternalObject(response);
			if(testCase.getAexpectedNodes()!=null && !testCase.getAexpectedNodes().isEmpty())
			{
				for (String node : testCase.getAexpectedNodes()) {
					List<String> nodeProps = getNodeProperties(node);
					
					String nodeVal = null;
					if(nodeProps.size()==1 || !hasValidationFunction(nodeProps.get(0))) {
						nodeVal = getNodeValue(intObj, nodeProps.get(0));
						Assert.assertNotNull("Expected Node value for " + nodeProps.get(0) + " is null", nodeVal);
					}
					if(nodeProps.size()>1) {
						String lhs = nodeProps.get(0);
						String lhsv = nodeVal;
						String oper = nodeProps.size()>2?nodeProps.get(1):"=";
						String rhsv = nodeProps.get(nodeProps.size()-1);
						doNodeLevelValidation(lhs, lhsv, oper, rhsv, context, testCase);
					}
				}
			}
			
			context.getWorkflowContextHandler().extractWorkFlowVariables(testCase, intObj, getType());
			
			if(context.getGatfExecutorConfig().isAuthEnabled() && context.getGatfExecutorConfig().getAuthUrl().equals(testCase.getUrl())) {
				String identifier = getNodeValue(intObj, context.getGatfExecutorConfig().getAuthExtractAuthParams()[0]);
				Assert.assertNotNull("Authentication token is null", identifier);
				context.setSessionIdentifier(identifier, testCase);
				context.getWorkflowContextHandler().getSuiteWorkflowContext(testCase)
					.put(context.getGatfExecutorConfig().getAuthExtractAuthParams()[2], identifier);
				Assert.assertNotNull("Authentication token is null", context.getSessionIdentifier(testCase));
			}
			testCaseReport.setStatus(TestStatus.Success.status);
		} catch (AssertionError e) {
			testCaseReport.setStatus(TestStatus.Failed.status);
			testCaseReport.setFailureReason(TestFailureReason.NodeValidationFailed.status);
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
				testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
			}
			e.printStackTrace();
		} catch (Throwable e) {
			testCaseReport.setStatus(TestStatus.Failed.status);
			testCaseReport.setFailureReason(TestFailureReason.Exception.status);
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
				testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
			}
			e.printStackTrace();
		}
	}
}
