package com.homeaway.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.reporter.configuration.ChartLocation;
import com.aventstack.extentreports.reporter.configuration.Protocol;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.homeaway.constants.Environment;

public class ExtentManager {

    private static ExtentReports extent;

    public static ExtentReports getInstance() {
        if (extent == null)
            createInstance("report/extent-report.html");

        return extent;
    }

    private static ExtentReports createInstance(String fileName) {
        extent = new ExtentReports();
        extent.setSystemInfo("Environment", Environment.envName);
        extent.setSystemInfo("User Email", Environment.userEmailAddress);
        extent.setSystemInfo("Databases Covered", getGroupsExecuted());
        ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter(fileName);
        htmlReporter.config().setTestViewChartLocation(ChartLocation.TOP);
        htmlReporter.config().setChartVisibilityOnOpen(true);
        htmlReporter.config().setTheme(Theme.DARK);
        htmlReporter.config().setEncoding("utf-8");
        htmlReporter.config().setDocumentTitle("Automation Result");
        htmlReporter.config().setReportName("DataPull Functional Test Automation Result");
        htmlReporter.config().setProtocol(Protocol.HTTPS);
        extent.attachReporter(htmlReporter);
        return extent;
    }

    public static String getGroupsExecuted() {
        String groups = System.getProperty("groups") == null ? "" : System.getProperty("groups");
        if (groups.equals("")) {
            groups = "all";
        } else {
            groups = groups.substring(0, groups.lastIndexOf(","));
        }
        return groups;
    }
}