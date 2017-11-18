package com.sismics.docs.rest;

import com.sismics.util.filter.TokenBasedSecurityFilter;
import org.junit.Assert;
import org.junit.Test;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;


/**
 * Test the app resource.
 * 
 * @author jtremeaux
 */
public class TestAppResource extends BaseJerseyTest {
    /**
     * Test the API resource.
     */
    @Test
    public void testAppResource() {
        // Login admin
        String adminToken = clientUtil.login("admin", "admin", false);
        
        // Check the application info
        JsonObject json = target().path("/app").request()
                .get(JsonObject.class);
        Assert.assertNotNull(json.getString("current_version"));
        Assert.assertNotNull(json.getString("min_version"));
        Long freeMemory = json.getJsonNumber("free_memory").longValue();
        Assert.assertTrue(freeMemory > 0);
        Long totalMemory = json.getJsonNumber("total_memory").longValue();
        Assert.assertTrue(totalMemory > 0 && totalMemory > freeMemory);
        Assert.assertFalse(json.getBoolean("guest_login"));

        // Rebuild Lucene index
        Response response = target().path("/app/batch/reindex").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
        
        // Clean storage
        response = target().path("/app/batch/clean_storage").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
        
        // Recompute quota
        response = target().path("/app/batch/recompute_quota").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
    }

    /**
     * Test the log resource.
     */
    @Test
    public void testLogResource() {
        // Login admin
        String adminToken = clientUtil.login("admin", "admin", false);
        
        // Check the logs (page 1)
        JsonObject json = target().path("/app/log")
                .queryParam("level", "DEBUG")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        JsonArray logs = json.getJsonArray("logs");
        Assert.assertTrue(logs.size() > 0);
        Long date1 = logs.getJsonObject(0).getJsonNumber("date").longValue();
        Long date2 = logs.getJsonObject(9).getJsonNumber("date").longValue();
        Assert.assertTrue(date1 >= date2);
        
        // Check the logs (page 2)
        json = target().path("/app/log")
                .queryParam("offset",  "10")
                .queryParam("level", "DEBUG")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        logs = json.getJsonArray("logs");
        Assert.assertTrue(logs.size() > 0);
        Long date3 = logs.getJsonObject(0).getJsonNumber("date").longValue();
        Long date4 = logs.getJsonObject(9).getJsonNumber("date").longValue();
        Assert.assertTrue(date3 >= date4);
    }

    /**
     * Test the guest login.
     */
    @Test
    public void testGuestLogin() {
        // Login admin
        String adminToken = clientUtil.login("admin", "admin", false);

        // Try to login as guest
        Response response = target().path("/user/login").request()
                .post(Entity.form(new Form()
                        .param("username", "guest")));
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Enable guest login
        target().path("/app/guest_login").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("enabled", "true")), JsonObject.class);

        // Login as guest
        String guestToken = clientUtil.login("guest", "", false);

        // Guest cannot delete himself
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .delete();
        Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        // Guest cannot see opened sessions
        JsonObject json = target().path("/user/session").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .get(JsonObject.class);
        Assert.assertEquals(0, json.getJsonArray("sessions").size());

        // Guest cannot delete opened sessions
        response = target().path("/user/session").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .delete();
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Guest cannot enable TOTP
        response = target().path("/user/enable_totp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Guest cannot disable TOTP
        response = target().path("/user/disable_totp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Guest cannot update itself
        response = target().path("/user").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .post(Entity.form(new Form()));
        Assert.assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());

        // Guest can see its documents
        target().path("/document/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, guestToken)
                .get(JsonObject.class);
    }

    /**
     * Test SMTP configuration changes.
     */
    @Test
    public void testSmtpConfiguration() {
        // Login admin
        String adminToken = clientUtil.login("admin", "admin", false);

        // Get SMTP configuration
        JsonObject json = target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertTrue(json.isNull("hostname"));
        Assert.assertTrue(json.isNull("port"));
        Assert.assertTrue(json.isNull("username"));
        Assert.assertTrue(json.isNull("password"));
        Assert.assertTrue(json.isNull("from"));

        // Change SMTP configuration
        target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("hostname", "smtp.sismics.com")
                        .param("port", "1234")
                        .param("username", "sismics")
                        .param("from", "contact@sismics.com")
                ), JsonObject.class);

        // Get SMTP configuration
        json = target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        Assert.assertEquals("smtp.sismics.com", json.getString("hostname"));
        Assert.assertEquals(1234, json.getInt("port"));
        Assert.assertEquals("sismics", json.getString("username"));
        Assert.assertTrue(json.isNull("password"));
        Assert.assertEquals("contact@sismics.com", json.getString("from"));
    }
}