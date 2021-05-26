<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <html>
            <body>
                <h2>Jenkins Build</h2>
                <h3>Building? <xsl:value-of select="workflowRun/building"/></h3>
                <h3>Display Name:  <xsl:value-of select="workflowRun/fullDisplayName"/></h3>
                <h3>Duration:  <xsl:value-of select="workflowRun/estimatedDuration"/></h3>
                <h3>Number:  <xsl:value-of select="workflowRun/number"/></h3>
                <h3>Queue Id:  <xsl:value-of select="workflowRun/queueId"/></h3>
                <h3>URL:  <xsl:value-of select="workflowRun/url"/></h3>
                <h3>result:  <xsl:value-of select="workflowRun/result"/></h3>
                <table border="1">
                    <tr bgcolor="#9acd32">
                        <th style="text-align:left">Short Description</th>
                        <th style="text-align:left">User Id</th>
                        <th style="text-align:left">Branch Name</th>
                        <th style="text-align:left">SHA1</th>
                        <th style="text-align:left">Remote Url</th>
                        <th style="text-align:left">User Name</th>
                        <th style="text-align:left">Building?</th>
                    </tr>
                    <xsl:for-each select="workflowRun/action">
                        <tr>
                            <td><xsl:value-of select="cause/shortDescription"/></td>
                            <td><xsl:value-of select="cause/userId"/></td>
                            <td><xsl:value-of select="cause/userName"/></td>
                            <td><xsl:value-of select="buildsByBranchName/revision/branch/name"/></td>
                            <td><xsl:value-of select="remoteUrl"/></td>
                        </tr>
                    </xsl:for-each>
                </table>

            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>