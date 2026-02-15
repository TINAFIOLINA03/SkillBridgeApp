import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.catalyst.advanced.CatalystAdvancedIOHandler;
import com.zc.component.object.ZCObject;
import com.zc.component.object.ZCTable;
import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class SkillBridge implements CatalystAdvancedIOHandler {
	private static final Logger LOGGER = Logger.getLogger(SkillBridge.class.getName());

	// Applied skills table name in Data Store
	private static final String TABLE_APPLICATION = "AppliedSkill";

	private void sendError(HttpServletResponse response, int status, String message) throws Exception {
		response.setStatus(status);
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
	}

	private void sendJson(HttpServletResponse response, int status, String json) throws Exception {
		response.setStatus(status);
		response.setContentType("application/json");
		response.getWriter().write(json);
	}

	private String escapeJson(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

	private String escapeZcql(String value) {
		if (value == null) return "";
		return value.replace("'", "\\'");
	}

	private String readRequestBody(HttpServletRequest request) throws Exception {
		BufferedReader reader = request.getReader();
		StringBuilder body = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			body.append(line);
		}
		return body.toString();
	}

	private String safeString(Object val) {
		if (val == null) return "";
		String s = String.valueOf(val);
		return "null".equals(s) ? "" : s;
	}

	// GET /api/learning
	private void handleGetLearning(HttpServletRequest request, HttpServletResponse response) throws Exception {
		try {
			ArrayList<ZCRowObject> rows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID, topic, category, source, CREATEDTIME FROM Learning"
			);

			// Count applied skills per learning_id so status is accurate
			Map<Long, Long> appliedCountByLearningId = new HashMap<Long, Long>();
			ArrayList<ZCRowObject> allApplied = ZCQL.getInstance().executeQuery(
				"SELECT learning_id FROM " + TABLE_APPLICATION
			);
			if (allApplied != null) {
				for (ZCRowObject ar : allApplied) {
					Object lid = ar.get(TABLE_APPLICATION, "learning_id");
					if (lid != null) {
						try {
							Long learningId = Long.parseLong(String.valueOf(lid));
							Long prev = appliedCountByLearningId.get(learningId);
							appliedCountByLearningId.put(learningId, (prev != null ? prev : 0L) + 1L);
						} catch (NumberFormatException e) {
							/* ignore invalid learning_id */
						}
					}
				}
			}

			StringBuilder json = new StringBuilder("[");
			boolean first = true;

			if (rows != null) {
				for (ZCRowObject row : rows) {
					String rowId = safeString(row.get("Learning", "ROWID"));
					String topic = safeString(row.get("Learning", "topic"));
					String category = safeString(row.get("Learning", "category"));
					String source = safeString(row.get("Learning", "source"));
					String createdTime = safeString(row.get("Learning", "CREATEDTIME"));

					long appliedCount = 0;
					try {
						Long lid = Long.parseLong(rowId);
						Long count = appliedCountByLearningId.get(lid);
						appliedCount = (count != null ? count : 0L);
					} catch (NumberFormatException e) {
						/* ignore */
					}

					String status = appliedCount > 0 ? "APPLIED" : "PENDING";

					if (!first) json.append(",");
					first = false;

					json.append("{");
					json.append("\"id\":\"").append(escapeJson(rowId)).append("\",");
					json.append("\"topic\":\"").append(escapeJson(topic)).append("\",");
					json.append("\"category\":\"").append(escapeJson(category)).append("\",");
					json.append("\"source\":\"").append(escapeJson(source)).append("\",");
					json.append("\"createdTime\":\"").append(escapeJson(createdTime)).append("\",");
					json.append("\"appliedCount\":").append(appliedCount).append(",");
					json.append("\"status\":\"").append(status).append("\"");
					json.append("}");
				}
			}

			json.append("]");
			sendJson(response, 200, json.toString());
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in handleGetLearning: " + e.getMessage(), e);
			sendError(response, 500, "Failed to fetch learning: " + e.getMessage());
		}
	}

	// POST /api/learning
	private void handlePostLearning(HttpServletRequest request, HttpServletResponse response) throws Exception {
		try {
			String body = readRequestBody(request);
			JSONParser parser = new JSONParser();
			JSONObject jsonData = (JSONObject) parser.parse(body);

			String topic = jsonData.get("topic") != null ? String.valueOf(jsonData.get("topic")) : null;
			String category = jsonData.get("category") != null ? String.valueOf(jsonData.get("category")) : null;
			String source = jsonData.get("source") != null ? String.valueOf(jsonData.get("source")) : null;

			if (topic == null || topic.trim().isEmpty() || category == null || category.trim().isEmpty()) {
				sendError(response, 400, "topic and category are required");
				return;
			}

			ZCTable table = ZCObject.getInstance().getTable("Learning");
			ZCRowObject row = ZCRowObject.getInstance();
			row.set("topic", topic.trim());
			row.set("category", category.trim());
			if (source != null && !source.trim().isEmpty()) {
				row.set("source", source.trim());
			}

			ZCRowObject insertedRow = table.insertRow(row);
			String rowId = safeString(insertedRow.get("ROWID"));

			StringBuilder json = new StringBuilder("{");
			json.append("\"id\":\"").append(escapeJson(rowId)).append("\",");
			json.append("\"topic\":\"").append(escapeJson(topic.trim())).append("\",");
			json.append("\"category\":\"").append(escapeJson(category.trim())).append("\",");
			json.append("\"source\":\"").append(escapeJson(source != null ? source.trim() : "")).append("\"");
			json.append("}");

			sendJson(response, 201, json.toString());
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in handlePostLearning: " + e.getMessage(), e);
			sendError(response, 500, "Failed to create learning: " + e.getMessage());
		}
	}

	// GET /api/learning/{id}
	private void handleGetLearningById(HttpServletRequest request, HttpServletResponse response, String learningId) throws Exception {
		Long learningIdLong;
		try {
			learningIdLong = Long.parseLong(learningId);
		} catch (NumberFormatException e) {
			sendError(response, 400, "Invalid learning ID");
			return;
		}

		try {
			ArrayList<ZCRowObject> rows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID, topic, category, source, CREATEDTIME FROM Learning WHERE ROWID = " + learningIdLong
			);

			if (rows == null || rows.isEmpty()) {
				sendError(response, 404, "Learning not found");
				return;
			}

			ZCRowObject learningRow = rows.get(0);
			String rowId = safeString(learningRow.get("Learning", "ROWID"));
			String topic = safeString(learningRow.get("Learning", "topic"));
			String category = safeString(learningRow.get("Learning", "category"));
			String source = safeString(learningRow.get("Learning", "source"));
			String createdTime = safeString(learningRow.get("Learning", "CREATEDTIME"));

			ArrayList<ZCRowObject> appliedRows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID, type, notes, applied_action, CREATEDTIME FROM " + TABLE_APPLICATION + " WHERE learning_id = " + learningIdLong
			);

			StringBuilder appliedJson = new StringBuilder("[");
			boolean first = true;
			long appliedCount = 0;

			if (appliedRows != null) {
				for (ZCRowObject appliedRow : appliedRows) {
					appliedCount++;
					if (!first) appliedJson.append(",");
					first = false;

					String appliedId = safeString(appliedRow.get(TABLE_APPLICATION, "ROWID"));
					String type = safeString(appliedRow.get(TABLE_APPLICATION, "type"));
					String notes = safeString(appliedRow.get(TABLE_APPLICATION, "notes"));
					String appliedAction = safeString(appliedRow.get(TABLE_APPLICATION, "applied_action"));
					String appliedCreatedTime = safeString(appliedRow.get(TABLE_APPLICATION, "CREATEDTIME"));

					appliedJson.append("{");
					appliedJson.append("\"id\":\"").append(escapeJson(appliedId)).append("\",");
					appliedJson.append("\"type\":\"").append(escapeJson(type)).append("\",");
					appliedJson.append("\"notes\":\"").append(escapeJson(notes)).append("\",");
					appliedJson.append("\"applied_action\":\"").append(escapeJson(appliedAction)).append("\",");
					appliedJson.append("\"createdTime\":\"").append(escapeJson(appliedCreatedTime)).append("\"");
					appliedJson.append("}");
				}
			}
			appliedJson.append("]");

			String status = appliedCount > 0 ? "APPLIED" : "PENDING";

			StringBuilder json = new StringBuilder("{");
			json.append("\"learning\":{");
			json.append("\"id\":\"").append(escapeJson(rowId)).append("\",");
			json.append("\"topic\":\"").append(escapeJson(topic)).append("\",");
			json.append("\"category\":\"").append(escapeJson(category)).append("\",");
			json.append("\"source\":\"").append(escapeJson(source)).append("\",");
			json.append("\"createdTime\":\"").append(escapeJson(createdTime)).append("\",");
			json.append("\"appliedCount\":").append(appliedCount).append(",");
			json.append("\"status\":\"").append(status).append("\"");
			json.append("},");
			json.append("\"appliedSkills\":").append(appliedJson.toString());
			json.append("}");

			sendJson(response, 200, json.toString());
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in handleGetLearningById: " + e.getMessage(), e);
			sendError(response, 500, "Failed to fetch learning detail: " + e.getMessage());
		}
	}

	// POST /api/learning/{id}/applied
	private void handlePostAppliedSkill(HttpServletRequest request, HttpServletResponse response, String learningId) throws Exception {
		Long learningIdLong;
		try {
			learningIdLong = Long.parseLong(learningId);
		} catch (NumberFormatException e) {
			sendError(response, 400, "Invalid learning ID");
			return;
		}

		try {
			ArrayList<ZCRowObject> validateRows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID FROM Learning WHERE ROWID = " + learningIdLong
			);

			if (validateRows == null || validateRows.isEmpty()) {
				sendError(response, 404, "Learning not found");
				return;
			}

			String body = readRequestBody(request);
			JSONParser parser = new JSONParser();
			JSONObject jsonData = (JSONObject) parser.parse(body);

			String type = jsonData.get("type") != null ? String.valueOf(jsonData.get("type")) : null;
			String notes = jsonData.get("notes") != null ? String.valueOf(jsonData.get("notes")) : null;
			String appliedAction = jsonData.get("applied_action") != null ? String.valueOf(jsonData.get("applied_action")) : null;

			if (type == null || type.trim().isEmpty()) {
				sendError(response, 400, "type is required");
				return;
			}

			ZCTable table = ZCObject.getInstance().getTable(TABLE_APPLICATION);
			ZCRowObject row = ZCRowObject.getInstance();
			row.set("learning_id", learningIdLong);
			row.set("type", type.trim());
			if (notes != null && !notes.trim().isEmpty()) {
				row.set("notes", notes.trim());
			}
			if (appliedAction != null && !appliedAction.trim().isEmpty()) {
				row.set("applied_action", appliedAction.trim());
			}

			ZCRowObject insertedRow = table.insertRow(row);
			String rowId = safeString(insertedRow.get("ROWID"));

			StringBuilder json = new StringBuilder("{");
			json.append("\"id\":\"").append(escapeJson(rowId)).append("\",");
			json.append("\"learningId\":\"").append(learningId).append("\",");
			json.append("\"type\":\"").append(escapeJson(type.trim())).append("\",");
			json.append("\"notes\":\"").append(escapeJson(notes != null ? notes.trim() : "")).append("\",");
			json.append("\"applied_action\":\"").append(escapeJson(appliedAction != null ? appliedAction.trim() : "")).append("\"");
			json.append("}");

			sendJson(response, 201, json.toString());
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in handlePostAppliedSkill: " + e.getMessage(), e);
			sendError(response, 500, "Failed to add applied skill: " + e.getMessage());
		}
	}

	// DELETE /api/learning/{id}
	private void handleDeleteLearning(HttpServletRequest request, HttpServletResponse response, String learningId) throws Exception {
		Long learningIdLong;
		try {
			learningIdLong = Long.parseLong(learningId);
		} catch (NumberFormatException e) {
			sendError(response, 400, "Invalid learning ID");
			return;
		}

		try {
			ArrayList<ZCRowObject> validateRows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID FROM Learning WHERE ROWID = " + learningIdLong
			);

			if (validateRows == null || validateRows.isEmpty()) {
				sendError(response, 404, "Learning not found");
				return;
			}

			ZCQL.getInstance().executeQuery(
				"DELETE FROM " + TABLE_APPLICATION + " WHERE learning_id = " + learningIdLong
			);
			ZCQL.getInstance().executeQuery(
				"DELETE FROM Learning WHERE ROWID = " + learningIdLong
			);

			sendJson(response, 200, "{\"success\":true}");
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in handleDeleteLearning: " + e.getMessage(), e);
			sendError(response, 500, "Failed to delete learning: " + e.getMessage());
		}
	}

	@Override
	public void runner(HttpServletRequest request, HttpServletResponse response) throws Exception {
		try {
			String uri = request.getRequestURI();
			String method = request.getMethod();

			LOGGER.log(Level.INFO, "Request: " + method + " " + uri);

			if ("GET".equals(method) && "/api/learning".equals(uri)) {
				handleGetLearning(request, response);
				return;
			}
			if ("POST".equals(method) && "/api/learning".equals(uri)) {
				handlePostLearning(request, response);
				return;
			}
			if ("GET".equals(method) && uri.startsWith("/api/learning/")) {
				String id = uri.substring("/api/learning/".length());
				if (!id.isEmpty() && !id.contains("/")) {
					handleGetLearningById(request, response, id);
					return;
				}
			}
			if ("DELETE".equals(method) && uri.startsWith("/api/learning/")) {
				String pathPart = uri.substring("/api/learning/".length());
				if (pathPart.endsWith("/applied")) return;
				String id = pathPart.contains("/") ? pathPart.substring(0, pathPart.indexOf("/")) : pathPart;
				if (!id.isEmpty()) {
					handleDeleteLearning(request, response, id);
					return;
				}
			}
			if ("POST".equals(method) && uri.matches("/api/learning/\\d+/applied")) {
				String pathPart = uri.substring("/api/learning/".length());
				String id = pathPart.substring(0, pathPart.length() - "/applied".length());
				handlePostAppliedSkill(request, response, id);
				return;
			}

			if ("/".equals(uri)) {
				response.setStatus(200);
				response.getWriter().write("SkillBridge API is running");
			} else {
				sendError(response, 404, "Not found");
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Exception in SkillBridge", e);
			String errMsg = e.getMessage() != null ? e.getMessage() : "Internal server error";
			if (errMsg.contains("UnAuthorized")) {
				errMsg = "Data Store access unauthorized. Ensure tables Learning and " + TABLE_APPLICATION + " exist in Catalyst Data Store.";
			}
			response.setStatus(500);
			response.setContentType("application/json");
			response.getWriter().write("{\"error\":\"" + escapeJson(errMsg) + "\"}");
		}
	}
}
