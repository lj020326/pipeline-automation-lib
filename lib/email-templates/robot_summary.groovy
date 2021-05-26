<%
 import java.text.DateFormat
 import java.text.SimpleDateFormat
 %>
 <!-- Robot Framework Results -->
 <%
 def robotResults = false
 def actions = build.actions // List<hudson.model.Action>
 actions.each() { action ->
    if( action.class.simpleName.equals("RobotBuildAction") ) { // hudson.plugins.robot.RobotBuildAction
        robotResults = true %>

      <p><h4>Robot Test Summary:</h4></p>
      <table cellspacing="0" cellpadding="4" border="1" align="left">
        <thead>
          <tr bgcolor="#F3F3F3">
            <td><b>Type</b></td>
            <td><b>Total</b></td>
            <td><b>Passed</b></td>
            <td><b>Failed</b></td>
            <td><b>Pass %</b></td>
          </tr>
        </thead>

        <tbody>

          <tr><td><b>All Tests</b></td>
            <td><%= action.result.overallTotal %></td>
            <td><%= action.result.overallPassed %></td>
            <td><%= action.result.overallFailed %></td>
            <td><%= action.overallPassPercentage %></td>
          </tr>

          <tr><td><b>Critical Tests</b></td>
            <td><%= action.result.criticalTotal %></td>
            <td><%= action.result.criticalPassed %></td>
            <td><%= action.result.criticalFailed %></td>
            <td><%= action.criticalPassPercentage %></td>
          </tr>

        </tbody>
      </table><%
    } // robot results
 }
 if (!robotResults) { %>
 <p>No Robot Framework test results found.</p>
 <%
 } %>