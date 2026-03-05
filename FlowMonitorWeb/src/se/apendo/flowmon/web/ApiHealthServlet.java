package se.apendo.flowmon.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import se.apendo.flowmon.core.PollerService;

/**
 * /api/health
 *
 * DB-probe för "Live"-indikator i GUI.
 * Vi kör INTE count på flöden (kan vara 0 även när DB är OK),
 * utan en minimal probe som alltid ska ge 1 rad om connection fungerar:
 *
 *   VALUES 1
 *
 * DataSource hämtas från PollerService (enda stället som känner till JNDI,
 * t.ex. JNDI_DS_2). Ingen dubblering av JNDI-konfiguration här.
 */
public class ApiHealthServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("application/json");

    long t0 = System.currentTimeMillis();

    PollerService poller = (PollerService) req.getServletContext().getAttribute(PollerService.CTX_KEY);
    if (poller == null) {
      writeJson(resp, 500, false, "PollerService saknas i ServletContext", System.currentTimeMillis() - t0);
      return;
    }

    DataSource ds = poller.getDataSource();
    if (ds == null) {
      writeJson(resp, 500, false, "DataSource saknas (poller ej startad?)", System.currentTimeMillis() - t0);
      return;
    }

    Connection c = null;
    PreparedStatement ps = null;
    ResultSet rs = null;

    try {
      c = ds.getConnection();
      ps = c.prepareStatement("VALUES 1");
      rs = ps.executeQuery();

      if (rs.next()) {
        writeJson(resp, 200, true, null, System.currentTimeMillis() - t0);
      } else {
        writeJson(resp, 500, false, "Tomt svar från probe", System.currentTimeMillis() - t0);
      }
    } catch (Exception e) {
      writeJson(resp, 500, false, safeMsg(e), System.currentTimeMillis() - t0);
    } finally {
      try { if (rs != null) rs.close(); } catch (Exception ignore) {}
      try { if (ps != null) ps.close(); } catch (Exception ignore) {}
      try { if (c != null) c.close(); } catch (Exception ignore) {}
    }
  }

  private static String safeMsg(Throwable t) {
    if (t == null) return "okänt fel";
    String m = t.getMessage();
    return (m != null && m.length() > 0) ? m : t.toString();
  }

  private static void writeJson(HttpServletResponse resp, int status, boolean ok, String error, long ms) throws IOException {
    resp.setStatus(status);

    StringBuilder sb = new StringBuilder(160);
    sb.append('{');
    sb.append("\"ok\":").append(ok ? "true" : "false");
    sb.append(",\"ms\":").append(ms);

    if (error != null) {
      sb.append(",\"error\":\"").append(jsonEscape(error)).append('\"');
    }

    sb.append('}');
    resp.getWriter().write(sb.toString());
  }

  private static String jsonEscape(String s) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      switch (ch) {
        case '\\': sb.append("\\\\"); break;
        case '"':  sb.append("\\\""); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        default:
          if (ch < 0x20) sb.append(' ');
          else sb.append(ch);
      }
    }
    return sb.toString();
  }
}


