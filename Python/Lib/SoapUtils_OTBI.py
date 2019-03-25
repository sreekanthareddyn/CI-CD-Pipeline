import requests
import urllib
import urllib2
import os
import logging
import sys
import argparse
from string import Template
from xml.etree import ElementTree

#from requests.packages.urllib3.exceptions import InsecureRequestWarning
#requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

soapAPIResources = os.path.abspath("../resources/otbi")

bstrParam1 = "My"
bstrParam2 = "Test"
# logout of Sandbox

def Method1():
    thisEndPoint = "http://www.soapclient.com/xml/soapresponder.wsdl"
    headers = { "Content-Type" : "text/xml" }
    soapMethodReqTemplate = Template(open(soapAPIResources + "/Method1.xml").read())
    soapMethodReq = { "bstrParam1" : bstrParam1, "bstrParam2" : bstrParam2 }
    soapReq = soapMethodReqTemplate.substitute(soapMethodReq)
    response = requests.post(thisEndPoint,data=soapReq,headers=headers,verify=False)
    print(response.text)
    print(response.status_code)
    return response.status_code

def createObject(folderAbsolutePathURL, userID, password):
    headers = { "Content-Type" : "text/xml", "SOAPAction" : "createObject", "charset" : "UTF-8"  }
    soapCreateObjectReqTemplate = Template(open(soapAPIResources + "/createObject.xml").read())
    soapCreateObjectReq = { "folderAbsolutePathURL" : folderAbsolutePathURL, "objectName" : objectName, "objectType" : objectType, "userID" : userID, "password" : password }
    soapReq = soapCreateObjectReqTemplate.substitute(soapCreateObjectReq)
    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)
    print(response.text)
    print(response.status_code)
    return response.status_code

def getObject(objectAbsolutePath, userID, password):
    headers = { "Content-Type" : "text/xml", "SOAPAction" : "getObject", "charset" : "UTF-8"  }
    soapGetObjectReqTemplate = Template(open(soapAPIResources + "/getObject.xml").read())
    soapGetObjectReq = { "objectAbsolutePath" : objectAbsolutePath, "userID" : userID, "password" : password }
    soapReq = soapGetObjectReqTemplate.substitute(soapGetObjectReq)
    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)
    print(response.text)
    print(response.status_code)
    return response.status_code

def updateObject(objectAbsolutePath, userID, password):
    headers = { "Content-Type" : "text/xml", "SOAPAction" : "updateObject", "charset" : "UTF-8"  }
    soapUpdateObjectReqTemplate = Template(open(soapAPIResources + "/updateObject.xml").read())
    soapUpdateObjectReq = { "objectAbsolutePath" : objectAbsolutePath, "userID" : userID, "password" : password }
    soapReq = soapUpdateObjectReqTemplate.substitute(soapUpdateObjectReq)
    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)
    print(response.text)
    print(response.status_code)
    return response.status_code

def generate_argparser():

    parser = argparse.ArgumentParser(description='Send SOAP Requests to OTBI Application')

    parser.add_argument("--wsdl", type=str, help='The WSDL Url')
    parser.add_argument("--directory", type=str, help='Directory to get all files from')

def main():
    global endPoint, logger

    logger=initialise_logger()

    logger.info("Send SOAP Requests to OTBI Application")
    parser = argparse.ArgumentParser(description='Send SOAP Requests to OTBI Application')
    parser.add_argument("--wsdl", type=str, help='The WSDL Url')
    parser.add_argument("--directory", type=str, help='Directory to get all files from')

    args = parser.parse_args()

    if (args.name1 is not None and args.name2 is not None):

    else:
        parser.print_help()
        sys.exit(1)
