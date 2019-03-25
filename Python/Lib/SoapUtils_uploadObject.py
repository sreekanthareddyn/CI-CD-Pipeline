import requests
import os
import logging
import sys
import argparse
from string import Template

#from requests.packages.urllib3.exceptions import InsecureRequestWarning
#requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

soapAPIResources = os.path.abspath("../resources/otbi")
# logout of Sandbox

def uploadObject(reportObjectAbsolutePathURL, objectType, objectZippedData , userID, password):
    headers = { "Content-Type" : "text/xml", "SOAPAction" : "uploadObject", "charset" : "UTF-8"  }
    soapUploadObjectReqTemplate = Template(open(soapAPIResources + "/uploadObject.xml").read())
    soapUploadObjectReq = { "reportObjectAbsolutePathURL" : reportObjectAbsolutePathURL, "objectType" : objectType, "objectZippedData" : objectZippedData, "userID" : userID, "password" : password }
    soapReq = soapUploadObjectReqTemplate.substitute(soapUploadObjectReq)
    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)
    logger.info(response.text)
    logger.info("Status Code:" + str(response.status_code))
    statusCode = response.status_code
    return statusCode

def generate_argparser():
    parser = argparse.ArgumentParser(description='Send SOAP Requests to Test API')
    parser.add_argument("-w", "--wsdl", type=str, required=True, help='URL of the wsdl')
    parser.add_argument("-d" "--directory", type=str, required=True, help='The file location')
    parser.add_argument("-u", "--username", type=str, required=True, help='OTBI username')
    parser.add_argument("-p", "--password", type=str, required=True, help='OTBI password')
    parser.add_argument("-t", "--encodedText", type=str, required=True, help='Base64 encoded file contents')
    parser.add_argument("-f", "--filename", type=str, required=True, help='Name of the base64 encoded file')
    parser.add_argument("-e", "--ext", type=str, required=True, help='File extension of the base64 encoded file')
    return parser

def initialise_logger():
    	# create logger
	logger = logging.getLogger('logger')

	logger.setLevel(logging.DEBUG)
	# create console handler and set level to debug
	ch = logging.StreamHandler()
	ch.setLevel(logging.DEBUG)
	# create formatter
	formatter = logging.Formatter("%(asctime)s:%(levelname)s > %(message)s",
                              "%Y-%m-%d %H:%M:%S")
	# add formatter to ch
	ch.setFormatter(formatter)

	# add ch to logger
	logger.addHandler(ch)

	return logger

def main():
    global endPoint, logger, fileDirectory

    logger=initialise_logger()

    logger.info("Send SOAP Requests to OTBI Application")

    parser = generate_argparser()
    args = parser.parse_args()

    if (args.wsdl is not None and args.directory is not None and args.username is not None and
        args.password is not None and args.encodedText is not None and args.filename is not None and
        args.ext is not None):

        fileDirectory = os.path.abspath(args.directory)
        endPoint = args.wsdl
        objectType = args.ext
        objectZippedData = args.encodedText
        userID = args.username
        password = args.password

        reportObjectAbsolutePathURL = None
        FileFound = False

        for file in os.listdir(fileDirectory):
            if file.endswith(".txt"):
                fullFilePath = os.path.join(fileDirectory, file)
                logger.info("Text File Location: " + fullFilePath)
                reportObjectAbsolutePathURL = open(fullFilePath).read().strip()
                logger.info("reportObjectAbsolutePathURL: " + reportObjectAbsolutePathURL)
                FileFound = True
                break

        if not FileFound:
            logger.info("Error: Text File Not Found")
            sys.exit(5)

        statusCode = uploadObject(reportObjectAbsolutePathURL, objectType, objectZippedData,
            userID, password)

        if(statusCode == 200):
            logger.info("Successful: Object Uploaded")
            sys.exit(0)
        else:
            logger.info(statusCode + ": Failed SOAP Request")
            sys.exit(3)
    else:
        parser.print_help()
        sys.exit(1)

if __name__ == '__main__':
	main()
