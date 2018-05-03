package com.amadeus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Arrays;
import lombok.Getter;
import lombok.ToString;

/**
 * A generic response as received from an API call. Contains the status code, body,
 * and parsed JSON (if any).
 */
@ToString
public class Response {
  /**
   * The HTTP status code for the response, if any.
   */
  private @Getter int statusCode;
  /**
   * Wether the raw body has been parsed into JSON.
   */
  private @Getter boolean parsed;
  /**
   * The parsed JSON received from the API, if the result was JSON.
   */
  private @Getter JsonObject result;
  /**
   * The data extracted from the JSON data - if the body contained JSON.
   */
  private @Getter JsonArray data;
  /**
   * The raw body received from the API.
   */
  private @Getter String body;
  /**
   * The actual Request object used to make this API call.
   */
  private @Getter Request request;

  public Response(Request request) {
    this.request = request;
  }

  // Tries to parse the raw response from the request.
  protected void parse(HTTPClient client) {
    parseStatusCode();
    parseData(client);
  }

  // Detects of any errors have occured and throws the appropriate errors.
  protected void detectError(HTTPClient client) throws ResponseException {
    ResponseException exception = null;
    if (statusCode >= 500) {
      exception = new ServerException(this);
    } else if (statusCode == 404) {
      exception = new NotFoundException(this);
    } else if (statusCode == 401) {
      exception = new AuthenticationException(this);
    } else if (statusCode >= 400) {
      exception = new ClientException(this);
    } else if (!parsed) {
      exception = new ParserException(this);
    }

    if (exception != null) {
      exception.log(client);
      throw exception;
    }
  }

  // Tries to parse the status code. Catches any errors and defaults to
  // status 0 if an error occurred.
  private void parseStatusCode() {
    try {
      this.statusCode = getRequest().getConnection().getResponseCode();
    } catch (IOException e) {
      this.statusCode = 0;
    }
  }

  // Tries to parse the data
  private void parseData(HTTPClient client) {
    this.parsed = false;
    this.body = readBody();
    this.result = parseJson(client);
    this.parsed = this.result != null;
    if (result.has("data")) {
      this.data = result.get("data").getAsJsonArray();
    }
  }

  // Tries to read the body.
  private String readBody() {
    // Get the connection
    HttpURLConnection connection = getRequest().getConnection();

    // Try to get the input stream
    InputStream inputStream = null;
    try {
      inputStream = connection.getInputStream();
    } catch (IOException e) {
      inputStream = connection.getErrorStream();
    }

    // Try to parse the input stream
    try {
      InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
      BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
      StringBuffer body = new StringBuffer();
      String inputLine;
      while ((inputLine = bufferedReader.readLine()) != null) {
        body.append(inputLine);
      }
      bufferedReader.close();
      // Return the response body
      return body.toString();
    } catch (IOException e) {
      System.out.println(e);
      // return null if we could not parse the input stream
      return null;
    }
  }

  // Ties to parse the response body into a JSON Object
  private JsonObject parseJson(HTTPClient client) {
    if (isJson()) {
      return new JsonParser().parse(getBody()).getAsJsonObject();
    }
    return null;
  }

  // Checks if the response is likely to be JSON.
  private boolean isJson() {
    return hasJsonHeader() && hasBody();
  }

  // Checks if the response headers include a JSON mime-type.
  private boolean hasJsonHeader() {
    String contentType = getRequest().getConnection().getHeaderField("Content-Type");
    String[] expectedContentTypes = new String[] {
      "application/json", "application/vnd.amadeus+json"
    };
    return Arrays.asList(expectedContentTypes).contains(contentType);
  }

  // Checks if the response has a body
  private boolean hasBody() {
    return getBody() != null;
  }
}