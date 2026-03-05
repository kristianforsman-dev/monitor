package se.apendo.flowmon.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * /api/monitoring-config
 *
 * GET  -> returns monitoring-config.json as JSON
 * POST -> overwrites monitoring-config.json with provided JSON body
 *
 * OBS: att skriva i en expanderad webapp kan försvinna vid redeploy.
 * Om ni vill göra det “rätt” senare: flytta filen till en extern path (init-param).
 */
public class ApiMonitoringConfigServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("application/json");
    resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    resp.setHeader("Pragma", "no-cache");

    try {
      ServletContext ctx = req.getServletContext();
      File cfgFile = new File(ctx.getRealPath("/assets/monitoring-config.json"));
      String json = slurp(cfgFile);
      if (json == null || json.trim().isEmpty()) json = "{\"defaults\":{},\"flows\":[]}";

      resp.getWriter().write(json);
      resp.getWriter().flush();
    } catch (Exception e) {
      try {
        resp.setStatus(500);
        resp.getWriter().write("{\"error\":\"monitoring-config failed\"}");
      } catch (Exception ignore) {}
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("application/json");
    resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    resp.setHeader("Pragma", "no-cache");

    SafeJsonWriter jw = null;
    try {
      jw = new SafeJsonWriter(resp.getWriter());

      ServletContext ctx = req.getServletContext();
      File cfgFile = new File(ctx.getRealPath("/assets/monitoring-config.json"));

      String body = readBody(req);
      if (body == null) body = "";
      body = body.trim();

      // enkel guard – vi vill inte råka spara skräp
      if (!body.startsWith("{") || !body.endsWith("}")) {
        resp.setStatus(400);
        jw.beginObject()
          .name("ok").value(false)
          .name("error").value("Body must be a JSON object")
        .endObject();
        jw.flush();
        return;
      }

      writeUtf8(cfgFile, body);

      jw.beginObject().name("ok").value(true).endObject();
      jw.flush();
    } catch (Exception e) {
      try {
        resp.setStatus(500);
        if (jw != null) {
          jw.beginObject()
            .name("ok").value(false)
            .name("error").value("save failed: " + e.getMessage())
          .endObject();
          jw.flush();
        }
      } catch (Exception ignore) {}
    }
  }

  private static String readBody(HttpServletRequest req) throws Exception {
    BufferedReader br = new BufferedReader(new InputStreamReader(req.getInputStream(), StandardCharsets.UTF_8));
    try {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) sb.append(line).append("\n");
      return sb.toString();
    } finally { try { br.close(); } catch (Exception ignore) {} }
  }

  private static String slurp(File f) throws Exception {
    if (f == null || !f.exists()) return "";
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
    try {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) sb.append(line).append("\n");
      return sb.toString();
    } finally { try { br.close(); } catch (Exception ignore) {} }
  }

  private static void writeUtf8(File f, String s) throws Exception {
    FileOutputStream fos = new FileOutputStream(f);
    OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
    try {
      w.write(s);
    } finally {
      try { w.close(); } catch (Exception ignore) {}
      try { fos.close(); } catch (Exception ignore) {}
    }
  }
}

