import requests
import urllib
import urllib2
import os
import logging
from string import Template
from xml.etree import ElementTree

#from requests.packages.urllib3.exceptions import InsecureRequestWarning
#requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

apiVersion = "42.0"
soapAPIResources = os.path.abspath("../../resources/soap-templates")

# build partner endpoints
def buildEndpoints(org):
    global endPoints
    endPoints = {
        "SOAP" : org + "/services/Soap/u/" + apiVersion,
        "TOOLS" : org + "/services/Soap/T/"+ apiVersion,
        "QUERY" : org + "/services/data/v" + apiVersion + "/query/",
        "SOBJECT" : org + "/services/data/v" + apiVersion + "/sobjects/",
        "SOBJECT_TOOLS" : org + "/services/data/v" + apiVersion +"/tooling/sobjects/",
        "BULK" : org + "/services/async/" + apiVersion,
        "APEX" : org + "/services/Soap/s/" + apiVersion
    }

def getEndPoint(endPoint):
    return endPoints[endPoint]

# login to Sandbox

def login(username,password,org):
    global response
    headers = { "Content-Type" : "text/xml","SOAPAction" : "login", "charset" : "UTF-8"  }
    endPoint = getEndPoint("SOAP")
    soapLoginReqTemplate = Template(open(soapAPIResources + "/login.xml").read())
    soapLoginReq = { "username" : username, "password" : password.replace("&","&#38;").replace("<","&lt;").replace(">","&gt;").replace('\'',"&#39;").replace("\"","&#34;")}
    soapReq = soapLoginReqTemplate.substitute(soapLoginReq)
    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    if(response.text.encode('utf-8').strip() is not None): 
        root = ElementTree.fromstring(response.text.encode('utf-8').strip())
        
        global sessionId,serverUrl,metdataServerUrl

        sessionId = None
        serverUrl = None
        metdataServerUrl = None

        for item in root.iter():
            if(item.tag == '{urn:partner.soap.sforce.com}sessionId'):
                sessionId = item.text
                
            if(item.tag == '{urn:partner.soap.sforce.com}serverUrl'):
                serverUrl = item.text
    
            if(item.tag == '{urn:partner.soap.sforce.com}metadataServerUrl'):
                metdataServerUrl = item.text
    
# logout of Sandbox

def logout():
    endPoint = getEndPoint("SOAP")
    headers = { "Content-Type" : "text/xml","SOAPAction" : "logout", "charset" : "UTF-8"  }
    soapLogoutReqTemplate = Template(open(soapAPIResources + "/logout.xml").read())
    soapLogoutReq = { "sessionId" : sessionId }
    soapReq = soapLogoutReqTemplate.substitute(soapLogoutReq)
    response = requests.delete(endPoint,data=soapReq,headers=headers,verify=False)

# send json post data to any endPoint, used for Heroku measures templates data load.
def sendJsonRequest(endPoint,payload):
    headers = { 'Authorization': 'Bearer ' + sessionId, 'Content-Type' : 'application/json' }
    response = requests.post(endPoint,data=open(payload, 'rb'),headers=headers)

    return response

# send json post data to any endPoint, used for Heroku measures templates data load.
def sendJsonStrRequest(endPoint,payload):
    endPoint = endPoint.replace("${APIVERSION}","v" + apiVersion)
    headers = { 'Authorization': 'Bearer ' + sessionId, 'Content-Type' : 'application/json' }
    response = requests.post(endPoint,data=payload,headers=headers)

    return response

# send json post data to any endPoint, used for Heroku measures templates data load.
def sendJsonStrPatchRequest(endPoint,payload):
    endPoint = endPoint.replace("${APIVERSION}","v" + apiVersion)
    headers = { 'Authorization': 'Bearer ' + sessionId, 'Content-Type' : 'application/json' }
    response = requests.patch(endPoint,data=payload,headers=headers)

    return response

# describe SObject

def describeSObject(objectName,recordId):
    endPoint = getEndPoint("SOBJECT")
    if(recordId is not None):
        endPoint = endPoint + objectName + "/" + recordId
    else:
        endPoint = endPoint + objectName + "/describe"
    headers = { 'Authorization': 'Bearer '+ sessionId }
    

    response = requests.get(endPoint,headers=headers,verify=False)

    return response

# describe SObject using TOOLS API

def describeToolsSObject(objectName,recordId):
    endPoint = getEndPoint("SOBJECT_TOOLS")
    if(recordId is not None):
        endPoint = endPoint + objectName + "/" + recordId
    else:
        endPoint = endPoint + objectName + "/describe"
    headers = { 'Authorization': 'Bearer '+ sessionId }
    
    response = requests.get(endPoint,headers=headers,verify=False)

    return response

# exec query over REST - QUERY
# exec query over SOAP - TOOLS

def execQuery(query,endPoint):
    
    if(endPoint == "TOOLS"):
        endPoint = getEndPoint("TOOLS")
        headers = { "Content-Type" : "text/xml","SOAPAction" : "query", "charset" : "UTF-8"  }
        soapQueryReqTemplate = Template(open(soapAPIResources + "/query.xml").read())
        soapQueryReq = { "sessionId" : sessionId, "queryString" : query }
        soapReq = soapQueryReqTemplate.substitute(soapQueryReq)

        response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)
        
    if(endPoint == "QUERY"):
        endPoint = getEndPoint("QUERY")
        headers = { 'Authorization': 'Bearer '+ sessionId }
        params = urllib.urlencode({'q': query})
        query_url = endPoint + "?" + params
        response = requests.get(query_url,headers=headers,verify=False)

    return response

# delete Metadata

def deleteMetadata(metadataType,metadataFullName):
     endPoint = metdataServerUrl
     headers = { "Content-Type" : "text/xml","SOAPAction" : "deleteMetadata", "charset" : "UTF-8"  }
     soapDelReqTemplate = Template(open(soapAPIResources + "/deleteMetadata.xml").read())
     soapDelReq = { "sessionId" : sessionId, "metadataType" : metadataType, "metadataFullName" : metadataFullName }
     soapReq = soapDelReqTemplate.substitute(soapDelReq)

     response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

     return response

# creates Global PickList Value

def createGlobalPicklistValue(metadataFullName):
    endPoint = metdataServerUrl
    headers = { "Content-Type" : "text/xml","SOAPAction" : "createMetadata", "charset" : "UTF-8"  }
    soapCreateReqTemplate = Template(open(soapAPIResources + "/createGlobalPicklistValue.xml").read())
    soapCreateReq = { "sessionId" : sessionId, "metadataFullName" : metadataFullName }
    soapReq = soapCreateReqTemplate.substitute(soapCreateReq)

    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response

# fetch Metadata from Org

def retrieveMetadata(packageName):
    endPoint = metdataServerUrl
    headers = { "Content-Type" : "text/xml","SOAPAction" : "retrieve", "charset" : "UTF-8"  }
    soapExportMetTemplate = Template(open(soapAPIResources + "/retrieveMetadata.xml").read())
    # create a soap request xml file for each package
    
    soapExportMetReq = { "sessionId" : sessionId, "metadataName" : packageName }
    soapReq = soapExportMetTemplate.substitute(soapExportMetReq)
        
    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response


def retrieveSingleMetadata(metFullName, metType):
    #metFullName
    #metType

    endPoint = metdataServerUrl
    headers = { "Content-Type" : "text/xml","SOAPAction" : "retrieve", "charset" : "UTF-8"  }
    soapExportMetTemplate = Template(open(soapAPIResources + "/retrieveSingleMetadata.xml").read())

    soapExportMetReq =  { "sessionId" : sessionId, "metFullName" : metFullName, "metType" : metType }
    soapReq = soapExportMetTemplate.substitute(soapExportMetReq)
        
    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response

# checks Async Retrieve Metadata status

def checkRetrieveStatus(asyncProcessId):
    endPoint = metdataServerUrl
    headers = { "Content-Type" : "text/xml","SOAPAction" : "checkRetrieveStatus", "charset" : "UTF-8"  }
    soapCheckRetTemplate = Template(open(soapAPIResources + "/checkRetrieveStatus.xml").read())

    soapCheckRetReq = { "sessionId" : sessionId, "asyncProcessId" : asyncProcessId }
    soapReq = soapCheckRetTemplate.substitute(soapCheckRetReq)

    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response

def deploy(base64EncodedZip):
    endPoint = metdataServerUrl
    headers = { "Content-Type" : "text/xml","SOAPAction" : "deploy", "charset" : "UTF-8"  }
    soapDeployTemplate = Template(open(soapAPIResources + "/deploy.xml").read())

    soapDeployReq = { "sessionId" : sessionId, "zipData" : base64EncodedZip }
    soapReq = soapDeployTemplate.substitute(soapDeployReq)

    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response

def checkDeployStatus(asyncProcessId):
    endPoint = metdataServerUrl
    headers = { "Content-Type" : "text/xml","SOAPAction" : "checkDeployStatus", "charset" : "UTF-8"  }
    soapCheckDeployTemplate = Template(open(soapAPIResources + "/checkDeployStatus.xml").read())

    soapDeployCheckReq = { "sessionId" : sessionId, "asyncProcessId" : asyncProcessId }
    soapReq = soapCheckDeployTemplate.substitute(soapDeployCheckReq)

    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response

def createCustomField(metadataFullName,customDataType,defaultValue,customLabel):
    endPoint = getEndPoint("TOOLS")
    headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8"  }
    soapCreateMetTemplate = Template(open(soapAPIResources + "/createCustomField.xml").read())

    soapCreateMetReq = { "sessionId" : sessionId, "metadataFullName" : metadataFullName, "customDataType" : customDataType, "defaultValue" : defaultValue, "customLabel" : customLabel }
    soapReq = soapCreateMetTemplate.substitute(soapCreateMetReq)

    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response

def createCustomPicklistField(metadataFullName,valuesetName,customLabel):
    endPoint = getEndPoint("TOOLS")
    headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8"  }
    soapCreateMetTemplate = Template(open(soapAPIResources + "/createCustomPicklistField.xml").read())

    soapCreateMetReq = { "sessionId" : sessionId, "metadataFullName" : metadataFullName, "customLabel" : customLabel, "valuesetName" : valuesetName }
    soapReq = soapCreateMetTemplate.substitute(soapCreateMetReq)

    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response

def createPromotionCalculationOrgLevel(DaysAfter,DaysBefore,OrganizationId):
     endPoint = getEndPoint("SOAP")
     headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8" + sessionId }
     soapPromCalReqTemplate = Template(open(soapAPIResources + "/createPromotionCalculationOrgLevel.xml").read())
     soapPromCalReq = { "sessionId" : sessionId, "DaysAfter" : DaysAfter, "DaysBefore" : DaysBefore, "OrganizationId" : OrganizationId }
     soapReq = soapPromCalReqTemplate.substitute(soapPromCalReq)
     response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)
     return response
	 
def createPromotionCalculationProfile(DaysAfter,DaysBefore,ProfileId):
     endPoint = getEndPoint("SOAP")
     headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8" + sessionId }
     soapPromCalReqTemplate = Template(open(soapAPIResources + "/createPromotionCalculationProfile.xml").read())
     soapPromCalReq = { "sessionId" : sessionId, "DaysAfter" : DaysAfter, "DaysBefore" : DaysBefore, "ProfileId" : ProfileId }
     soapReq = soapPromCalReqTemplate.substitute(soapPromCalReq)
     response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)
     return response
	 
def updatePromotionCalculation(Id,DaysAfter,DaysBefore):
     endPoint = getEndPoint("SOAP")
     headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8" + sessionId }
     soapPromCalReqTemplate = Template(open(soapAPIResources + "/updatePromotionCalculation.xml").read())
     soapPromCalReq = { "sessionId" : sessionId, "Id" : Id, "DaysAfter" : DaysAfter, "DaysBefore" : DaysBefore }
     soapReq = soapPromCalReqTemplate.substitute(soapPromCalReq)
     response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

     return response

def deleteConnectedApp(appName):

    headers = { "Content-Type" : "text/xml","SOAPAction" : "deleteMetadata", "charset" : "UTF-8", "SessionHeader" : sessionId  }
    soapDeleteConnAppTemplate = Template(open(soapAPIResources + "/deleteConnectedApp.xml").read())
    soapDeleteConnAppReq = { "sessionId" : sessionId, "appName" : appName}
    soapReq = soapDeleteConnAppTemplate.substitute(soapDeleteConnAppReq)
    response = requests.post(metdataServerUrl,data=soapReq,headers=headers,verify=False)

    return response

def createConnectedApp(appName,contactEmail,callbackURL,label,activity):
    
    headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8", "SessionHeader" : sessionId  }
    if(activity == "Refresh"):
        soapCreateConnAppTemplate = Template(open(soapAPIResources + "/createConnectedApp-Refresh.xml").read())
    if(activity == "Post-Deploy"):
        soapCreateConnAppTemplate = Template(open(soapAPIResources + "/createConnectedApp-PostDeploy.xml").read())
    
    soapCreateConnAppReq = { "sessionId" : sessionId, "appName" : appName, "contactEmail" : contactEmail, "callbackURL" : callbackURL, "masterLabel" : label }
    soapReq = soapCreateConnAppTemplate.substitute(soapCreateConnAppReq)
    response = requests.post(metdataServerUrl,data=soapReq,headers=headers,verify=False)

    return response

def createNamedCredential(developerName,endPoint,generateAuthorizationHeader,allowMergeFieldsInBody,allowMergeFieldsInHeader,label, protocol,principalType):    
    headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8"  }
    soapCreateNamedCredTemplate = Template(open(soapAPIResources + "/createNamedCredential.xml").read())
    soapCreateNamedCredReq = { "sessionId" : sessionId, "DeveloperName" : developerName, "Label" : label, \
                                "EndPoint" : endPoint, "GenerateAuthorizationHeader" : generateAuthorizationHeader, \
                                "AllowMergeFieldsInBody" : allowMergeFieldsInBody, "AllowMergeFieldsInHeader" : allowMergeFieldsInHeader, \
                                "protocol": protocol, "PrincipalType" : principalType}
    soapReq = soapCreateNamedCredTemplate.substitute(soapCreateNamedCredReq)
    response = requests.post(metdataServerUrl,data=soapReq,headers=headers,verify=False)

    return response

def createUserProfile(fullName,userLicense):
    headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8", "SessionHeader" : sessionId  }
    soapCreateProfileTemplate = Template(open(soapAPIResources + "/createProfile.xml").read())
    soapCreateProfileReq = { "sessionId" : sessionId, "profileName" : fullName, "profileLicense" : userLicense }
    soapReq = soapCreateProfileTemplate.substitute(soapCreateProfileReq)
    response = requests.post(metdataServerUrl,data=soapReq,headers=headers,verify=False)

    return response
    
def execApex(apexString):
     endPoint = getEndPoint("APEX")
     headers = { "Content-Type" : "text/xml","SOAPAction" : "executeAnonymous", "charset" : "UTF-8"  }
     soapApexReqTemplate = Template(open(soapAPIResources + "/execApex.xml").read())
     soapApexReq = { "sessionId" : sessionId, "apexString" : apexString }
     soapReq = soapApexReqTemplate.substitute(soapApexReq)
     response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

     return response    
    
def updateMetadata(metadataType,metadata,fullName):
    endPoint=metdataServerUrl
    headers = { "Content-Type" : "text/xml","SOAPAction" : "updateMetadata", "charset" : "UTF-8"  }
    soapUpdateMetTemplate = Template(open(soapAPIResources + "/updateMetadata.xml").read())
    soapUpdateReq = { "sessionId" : sessionId, "metadataType" : metadataType, "metadata" : metadata.replace("&","&#38;").replace('\'',"&#39;").replace("\"","&#34;"), "metadataFullName" : fullName }
    soapReq = soapUpdateMetTemplate.substitute(soapUpdateReq)
    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response

def createCertificate(certFullName,certKeySize,certLabel,certPrivateKeyExportable):
    endPoint = metdataServerUrl
    headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8"  }

    soapCreateCertTemplate = Template(open(soapAPIResources + "/createCertificate.xml").read())
    soapCreateReq = { "sessionId" : sessionId, "certFullName" : certFullName, "certKeySize" : certKeySize, "certLabel" : certLabel, "certPrivateKeyExportable" : certPrivateKeyExportable }
    soapReq = soapCreateCertTemplate.substitute(soapCreateReq)

    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

    return response

def createEmailService(functionId,email,contextUserId):
     endPoint = getEndPoint("SOAP")
     headers = { "Content-Type" : "text/xml","SOAPAction" : "create", "charset" : "UTF-8"  }
     soapEmailReqTemplate = Template(open(soapAPIResources + "/createEmailService.xml").read())
     soapEmailReq = { "sessionId" : sessionId, "functionId" : functionId, "email" : email, "contextUserId" : contextUserId}
     soapReq = soapEmailReqTemplate.substitute(soapEmailReq)

     response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)

     return response

# Bulk API Calls, used for exporting large volumes of data from an Org

# creates a Bulk API Job

def createBulkJob(bulkOperation,bulkObjectName):
    endPoint = getEndPoint("BULK")
    endPoint = endPoint + "/job/"

    headers = { "Content-Type" : "text/xml", "charset" : "UTF-8", "X-SFDC-Session" : sessionId }
    bulkCreateJobTemplate = Template(open(soapAPIResources + "/createBulkJob.xml").read())

    bulkCreateJobReq = { "bulkOperation" : bulkOperation, "bulkObject" : bulkObjectName }
    bulkReq = bulkCreateJobTemplate.substitute(bulkCreateJobReq)

    response = requests.post(endPoint,data=bulkReq,headers=headers,verify=False)
    
    return response

def addBatchToBulkJob(bulkJobId,bulkObjectQuery):
    endPoint = getEndPoint("BULK")
    headers = { "Content-Type" : "text/csv", "charset" : "UTF-8", "X-SFDC-Session" : sessionId }

    endPoint = endPoint +  "/job/" + bulkJobId + "/batch"

    response = requests.post(endPoint,data=bulkObjectQuery,headers=headers,verify=False)

    return response

def checkBulkJobStatus(bulkJobId,batchId):
    endPoint = getEndPoint("BULK")
    headers = { "X-SFDC-Session" : sessionId }

    endPoint = endPoint + "/job/" + bulkJobId + "/batch/" + batchId

    response = requests.get(endPoint,headers=headers,verify=False)

    return response

def getBulkJobResultId(bulkJobId,batchId):
    endPoint = getEndPoint("BULK")
    headers = { "X-SFDC-Session" : sessionId }

    endPoint = endPoint + "/job/" + bulkJobId + "/batch/" + batchId + "/result"

    response = requests.get(endPoint,headers=headers,verify=False)

    return response

def getBulkJobResult(bulkJobId,batchId,bulkResultId):
    endPoint = getEndPoint("BULK")
    headers = { "X-SFDC-Session" : sessionId }

    endPoint = endPoint + "/job/" + bulkJobId + "/batch/" + batchId + "/result/" + bulkResultId

    response = requests.get(endPoint,headers=headers,verify=False)

    return response

def closeBulkJob(bulkJobId):
    endPoint = getEndPoint("BULK")
    headers = { "Content-Type" : "text/csv", "charset" : "UTF-8", "X-SFDC-Session" : sessionId }

    endPoint = endPoint + "/job/" + bulkJobId

    closeBulkReq = open(soapAPIResources + "/closeBulkJob.xml").read()

    response = requests.post(endPoint,data=closeBulkReq,headers=headers,verify=False)

    return response
    