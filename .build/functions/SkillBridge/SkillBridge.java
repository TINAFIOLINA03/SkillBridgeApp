import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.catalyst.advanced.CatalystAdvancedIOHandler;
import com.zc.component.object.ZCObject;
import com.zc.component.object.ZCTable;
import com.zc.component.object.ZCRowObject;
import com.zc.component.users.ZCUser;
import com.zc.component.ZCUserDetail;
import com.zc.component.zcql.ZCQL;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class SkillBridge implements CatalystAdvancedIOHandler {
	private static final Logger LOGGER = Logger.getLogger(SkillBridge.class.getName());

	// --- Utility Methods ---

	private Long getAuthenticatedUserId(HttpServletResponse response) throws Exception {
		try {
			ZCUserDetail currentUser = ZCUser.getInstance().getCurrentUser();
			if (currentUser == null) {
				sendError(response, 401, "Unauthorized");
				return null;
			}
			return currentUser.getUserId();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Auth check failed", e);
			sendError(response, 401, "Unauthorized");
			return null;
		}
	}

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

	// --- API Handlers ---

	// GET /api/learning
	private void handleGetLearning(HttpServletRequest request, HttpServletResponse response) throws Exception {
		Long userId = getAuthenticatedUserId(response);
		if (userId == null) return;

		try {
			// CREATORID is managed by Catalyst; only current user's rows are returned
			ArrayList<ZCRowObject> rows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID, topic, category, source FROM Learning"
			);

			StringBuilder json = new StringBuilder("[");
			boolean first = true;

			if (rows != null) {
				for (ZCRowObject row : rows) {
					String rowId = safeString(row.get("Learning", "ROWID"));
					String topic = safeString(row.get("Learning", "topic"));
					String category = safeString(row.get("Learning", "category"));
					String source = safeString(row.get("Learning", "source"));

					// Count applied skills for this learning
					ArrayList<ZCRowObject> countRows = ZCQL.getInstance().executeQuery(
						"SELECT COUNT(ROWID) FROM AppliedSkill WHERE learning_id = " + rowId
					);

					long appliedCount = 0;
					if (countRows != null && !countRows.isEmpty()) {
						Object countVal = countRows.get(0).get("AppliedSkill", "ROWID");
						if (countVal != null) {
							try {
								appliedCount = Long.parseLong(String.valueOf(countVal));
							} catch (NumberFormatException e) {
								appliedCount = 0;
							}
						}
					}

					String status = appliedCount > 0 ? "APPLIED" : "PENDING";

					if (!first) json.append(",");
					first = false;

					json.append("{");
					json.append("\"id\":\"").append(escapeJson(rowId)).append("\",");
					json.append("\"topic\":\"").append(escapeJson(topic)).append("\",");
					json.append("\"category\":\"").append(escapeJson(category)).append("\",");
					json.append("\"source\":\"").append(escapeJson(source)).append("\",");
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
		Long userId = getAuthenticatedUserId(response);
		if (userId == null) return;

		try {
			String body = readRequestBody(request);
			LOGGER.log(Level.INFO, "POST /api/learning body: " + body);

			JSONParser parser = new JSONParser();
			JSONObject jsonData = (JSONObject) parser.parse(body);

			String topic = jsonData.get("topic") != null ? String.valueOf(jsonData.get("topic")) : null;
			String category = jsonData.get("category") != null ? String.valueOf(jsonData.get("category")) : null;
			String source = jsonData.get("source") != null ? String.valueOf(jsonData.get("source")) : null;

			if (topic == null || topic.trim().isEmpty() || category == null || category.trim().isEmpty()) {
				sendError(response, 400, "topic and category are required");
				return;
			}

			// Use getTable() (not getTableInstance) to resolve the table via API call
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
		Long userId = getAuthenticatedUserId(response);
		if (userId == null) return;

		Long learningIdLong;
		try {
			learningIdLong = Long.parseLong(learningId);
		} catch (NumberFormatException e) {
			sendError(response, 400, "Invalid learning ID");
			return;
		}

		try {
			ArrayList<ZCRowObject> rows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID, topic, category, source FROM Learning WHERE ROWID = " + learningIdLong
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

			// Fetch applied skills
			ArrayList<ZCRowObject> appliedRows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID, type, notes FROM AppliedSkill WHERE learning_id = " + learningIdLong
			);

			StringBuilder appliedJson = new StringBuilder("[");
			boolean first = true;
			long appliedCount = 0;

			if (appliedRows != null) {
				for (ZCRowObject appliedRow : appliedRows) {
					appliedCount++;
					if (!first) appliedJson.append(",");
					first = false;

					String appliedId = safeString(appliedRow.get("AppliedSkill", "ROWID"));
					String type = safeString(appliedRow.get("AppliedSkill", "type"));
					String notes = safeString(appliedRow.get("AppliedSkill", "notes"));

					appliedJson.append("{");
					appliedJson.append("\"id\":\"").append(escapeJson(appliedId)).append("\",");
					appliedJson.append("\"type\":\"").append(escapeJson(type)).append("\",");
					appliedJson.append("\"notes\":\"").append(escapeJson(notes)).append("\"");
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
		Long userId = getAuthenticatedUserId(response);
		if (userId == null) return;

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

			if (type == null || type.trim().isEmpty()) {
				sendError(response, 400, "type is required");
				return;
			}

			// Use getTable() (not getTableInstance) to resolve the table via API call
			ZCTable table = ZCObject.getInstance().getTable("AppliedSkill");
			ZCRowObject row = ZCRowObject.getInstance();
			row.set("learning_id", learningIdLong);
			row.set("type", type.trim());
			if (notes != null && !notes.trim().isEmpty()) {
				row.set("notes", notes.trim());
			}

			ZCRowObject insertedRow = table.insertRow(row);
			String rowId = safeString(insertedRow.get("ROWID"));

			StringBuilder json = new StringBuilder("{");
			json.append("\"id\":\"").append(escapeJson(rowId)).append("\",");
			json.append("\"learningId\":\"").append(learningId).append("\",");
			json.append("\"type\":\"").append(escapeJson(type.trim())).append("\",");
			json.append("\"notes\":\"").append(escapeJson(notes != null ? notes.trim() : "")).append("\"");
			json.append("}");

			sendJson(response, 201, json.toString());
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in handlePostAppliedSkill: " + e.getMessage(), e);
			sendError(response, 500, "Failed to add applied skill: " + e.getMessage());
		}
	}

	// PUT /api/applied/{id}
	private void handlePutAppliedSkill(HttpServletRequest request, HttpServletResponse response, String appliedSkillId) throws Exception {
		Long userId = getAuthenticatedUserId(response);
		if (userId == null) return;

		Long appliedSkillIdLong;
		try {
			appliedSkillIdLong = Long.parseLong(appliedSkillId);
		} catch (NumberFormatException e) {
			sendError(response, 400, "Invalid applied skill ID");
			return;
		}

		try {
			// Validate ownership
			ArrayList<ZCRowObject> validateRows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID, learning_id FROM AppliedSkill WHERE ROWID = " + appliedSkillIdLong
			);

			if (validateRows == null || validateRows.isEmpty()) {
				sendError(response, 404, "Applied skill not found");
				return;
			}

			String body = readRequestBody(request);
			JSONParser parser = new JSONParser();
			JSONObject jsonData = (JSONObject) parser.parse(body);

			String type = jsonData.get("type") != null ? String.valueOf(jsonData.get("type")) : null;
			String notes = jsonData.get("notes") != null ? String.valueOf(jsonData.get("notes")) : null;

			if (type == null || type.trim().isEmpty()) {
				sendError(response, 400, "type is required");
				return;
			}

			String notesValue = (notes != null && !notes.trim().isEmpty()) ? notes.trim() : "";

			// Update via ZCQL
			ZCQL.getInstance().executeQuery(
				"UPDATE AppliedSkill SET type = '" + escapeZcql(type.trim()) + "', notes = '" + escapeZcql(notesValue) + "' WHERE ROWID = " + appliedSkillIdLong
			);

			StringBuilder json = new StringBuilder("{");
			json.append("\"id\":\"").append(appliedSkillId).append("\",");
			json.append("\"type\":\"").append(escapeJson(type.trim())).append("\",");
			json.append("\"notes\":\"").append(escapeJson(notesValue)).append("\"");
			json.append("}");

			sendJson(response, 200, json.toString());
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in handlePutAppliedSkill: " + e.getMessage(), e);
			sendError(response, 500, "Failed to update applied skill: " + e.getMessage());
		}
	}

	// DELETE /api/applied/{id}
	private void handleDeleteAppliedSkill(HttpServletRequest request, HttpServletResponse response, String appliedSkillId) throws Exception {
		Long userId = getAuthenticatedUserId(response);
		if (userId == null) return;

		Long appliedSkillIdLong;
		try {
			appliedSkillIdLong = Long.parseLong(appliedSkillId);
		} catch (NumberFormatException e) {
			sendError(response, 400, "Invalid applied skill ID");
			return;
		}

		try {
			// Validate ownership
			ArrayList<ZCRowObject> validateRows = ZCQL.getInstance().executeQuery(
				"SELECT ROWID FROM AppliedSkill WHERE ROWID = " + appliedSkillIdLong
			);

			if (validateRows == null || validateRows.isEmpty()) {
				sendError(response, 404, "Applied skill not found");
				return;
			}

			// Delete
			ZCQL.getInstance().executeQuery(
				"DELETE FROM AppliedSkill WHERE ROWID = " + appliedSkillIdLong
			);

			sendJson(response, 200, "{\"success\":true}");
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in handleDeleteAppliedSkill: " + e.getMessage(), e);
			sendError(response, 500, "Failed to delete applied skill: " + e.getMessage());
		}
	}

	// DELETE /api/learning/{id}
	private void handleDeleteLearning(HttpServletRequest request, HttpServletResponse response, String learningId) throws Exception {
		Long userId = getAuthenticatedUserId(response);
		if (userId == null) return;

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

			// Delete applied skills first (cascade)
			ZCQL.getInstance().executeQuery(
				"DELETE FROM AppliedSkill WHERE learning_id = " + learningIdLong
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

	// --- Main Router ---

	@Override
	public void runner(HttpServletRequest request, HttpServletResponse response) throws Exception {
		try {
			String uri = request.getRequestURI();
			String method = request.getMethod();

			LOGGER.log(Level.INFO, "Request: " + method + " " + uri);

			// API routes
			if ("GET".equals(method) && "/api/learning".equals(uri)) {
				handleGetLearning(request, response);
				return;
			}
			if ("POST".equals(method) && "/api/learning".equals(uri)) {
				handlePostLearning(request, response);
				return;
			}
			if ("GET".equals(method) && uri.startsWith("/api/learning/") && !uri.endsWith("/applied")) {
				String id = uri.substring("/api/learning/".length());
				handleGetLearningById(request, response, id);
				return;
			}
			if ("DELETE".equals(method) && uri.startsWith("/api/learning/") && !uri.endsWith("/applied")) {
				String id = uri.substring("/api/learning/".length());
				handleDeleteLearning(request, response, id);
				return;
			}
			if ("POST".equals(method) && uri.startsWith("/api/learning/") && uri.endsWith("/applied")) {
				String pathPart = uri.substring("/api/learning/".length());
				String id = pathPart.substring(0, pathPart.length() - "/applied".length());
				handlePostAppliedSkill(request, response, id);
				return;
			}
			if ("PUT".equals(method) && uri.startsWith("/api/applied/")) {
				String id = uri.substring("/api/applied/".length());
				handlePutAppliedSkill(request, response, id);
				return;
			}
			if ("DELETE".equals(method) && uri.startsWith("/api/applied/")) {
				String id = uri.substring("/api/applied/".length());
				handleDeleteAppliedSkill(request, response, id);
				return;
			}

			// Default routes
			if ("/".equals(uri)) {
				response.setStatus(200);
				response.getWriter().write("SkillBridge API is running");
			} else {
				sendError(response, 404, "Not found");
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Exception in SkillBridge", e);
			String errMsg = e.getMessage() != null ? e.getMessage() : "Internal server error";
			// Surface Catalyst SDK errors clearly
			if (errMsg.contains("UnAuthorized")) {
				errMsg = "Data Store access unauthorized. Ensure tables (Learning, AppliedSkill) exist in Catalyst Console > Data Store.";
			}
			response.setStatus(500);
			response.setContentType("application/json");
			response.getWriter().write("{\"error\":\"" + escapeJson(errMsg) + "\"}");
		}
	}
}
