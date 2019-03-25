import requests
import os
import logging
import sys
import argparse
from string import Template

soapAPIResources = os.path.abspath("../resources/otbi")
# logout of Sandbox

def Method1(bstrParam1, bstrParam2):
    headers = { "Content-Type" : "text/xml" }
    soapMethodReqTemplate = Template(open(soapAPIResources + "/Method1.xml").read())
    soapMethodReq = { "bstrParam1" : bstrParam1, "bstrParam2" : bstrParam2 }
    soapReq = soapMethodReqTemplate.substitute(soapMethodReq)
    response = requests.post(endPoint,data=soapReq,headers=headers,verify=False)
    logger.info(response.text)
    logger.info("Status Code:" + str(response.status_code))
    statusCode = response.status_code
    return statusCode

def generate_argparser():
    parser = argparse.ArgumentParser(description='Send SOAP Requests to Test API')
    parser.add_argument("-w", "--wsdl", type=str, required=True, help='The WSDL Url')
    parser.add_argument("-b1", "--bstrParam1", type=str, required=True, help='String 1')
    parser.add_argument("-b2", "--bstrParam2", type=str, required=True, help='String 2')
    parser.add_argument("-d", "--directory", type=str, required=True, help='Directory ')
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

    if (args.wsdl is not None and args.bstrParam1 is not None and args.bstrParam2 is not None):
        fileDirectory = os.path.abspath(args.directory)

        logger.info(fileDirectory)

        endPoint = args.wsdl
        bstrParam1 = args.bstrParam1
        bstrParam2 = args.bstrParam2

        reportObjectAbsolutePathURL = ""
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

        statusCode = Method1(bstrParam1, bstrParam2)

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
