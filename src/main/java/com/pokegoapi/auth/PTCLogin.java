package com.pokegoapi.auth;

import POGOProtos.Networking.EnvelopesOuterClass.Envelopes.RequestEnvelope.AuthInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pokegoapi.exceptions.LoginFailedException;
import okhttp3.*;


public class PTCLogin extends Login {
	public static final String CLIENT_SECRET = "w8ScCUXJQc6kXKw8FiOhd8Fixzht18Dq3PEVkUCP5ZPxtgyWsbTvWHFLm2wNY0JR";
	public static final String REDIRECT_URI = "https://www.nianticlabs.com/pokemongo/error";
	public static final String CLIENT_ID = "mobile-app_pokemon-go";

	public static final String API_URL = "https://pgorelease.nianticlabs.com/plfe/rpc";
	public static final String LOGIN_URL = "https://sso.pokemon.com/sso/login?service=https%3A%2F%2Fsso.pokemon.com%2Fsso%2Foauth2.0%2FcallbackAuthorize";
	public static final String LOGIN_OAUTH = "https://sso.pokemon.com/sso/oauth2.0/accessToken";

	public static final String USER_AGENT = "niantic";


	/**
	 * Returns an AuthInfo object given a token, this should not be an access token but rather an id_token
	 *
	 * @param String the id_token stored from a previous oauth attempt.
	 * @return AuthInfo a AuthInfo proto structure to be encapsulated in server requests
	 */
	public AuthInfo login(String token) {
		AuthInfo.Builder builder = AuthInfo.newBuilder();
		builder.setProvider("ptc");
		builder.setToken(AuthInfo.JWT.newBuilder().setContents(token).setUnknown2(59).build());
		return builder.build();
	}

	/**
	 * Starts a login flow for pokemon.com (PTC) using a username and password, this uses pokemon.com's oauth endpoint and returns a usable AuthInfo without user interaction
	 *
	 * @param String PTC username
	 * @param String PTC password
	 * @return AuthInfo a AuthInfo proto structure to be encapsulated in server requests
	 */
	public AuthInfo login(String username, String password) throws LoginFailedException {
    //TODO: stop creating an okhttp client per request

    OkHttpClient okHttpClient = new OkHttpClient();

		try {

      Request get = new Request.Builder()
              .url(LOGIN_URL)
              .header("User-Agent", USER_AGENT)
              .get()
              .build();

      Response getResponse = okHttpClient.newCall(get).execute();

			Gson gson = new GsonBuilder().create();

      PTCAuthJson ptcAuth = gson.fromJson(getResponse.body().string(), PTCAuthJson.class);


      HttpUrl url = new HttpUrl.Builder()
              .scheme("https")
              .addQueryParameter("lt", ptcAuth.getLt())
              .addQueryParameter("execution", ptcAuth.getExecution())
              .addQueryParameter("_eventId", ptcAuth.getExecution())
              .addQueryParameter("username", username)
              .addQueryParameter("password", password)
              .build();

      RequestBody reqBody = RequestBody.create(null, new byte[0]);

      Request postRequest = new Request.Builder()
              .url(url)
              .method("POST", reqBody)
              .build();

      Response response = okHttpClient.newCall(postRequest).execute();

      String body = response.body().string();

			if (body.length() > 0) {
				PTCError ptcError = gson.fromJson(body, PTCError.class);
				if (ptcError.getError() != null && ptcError.getError().length() > 0) {
					throw new LoginFailedException();
				}
			}

			String ticket = null;
      for (String location : response.headers("location")) {
        ticket = location.split("ticket=")[1];
			}

      url = HttpUrl.parse(LOGIN_OAUTH).newBuilder()
              .scheme("https")
              .addQueryParameter("client_id", CLIENT_ID)
              .addQueryParameter("redirect_uri", REDIRECT_URI)
              .addQueryParameter("client_secret", CLIENT_SECRET)
              .addQueryParameter("grant_type", "refresh_token")
              .addQueryParameter("code", ticket)
              .build();

      postRequest = new Request.Builder()
              .url(url)
              .method("POST", reqBody)
              .header("User-Agent", USER_AGENT)
              .build();

      response = okHttpClient.newCall(postRequest).execute();

      body = response.body().string();

			String token;
			try {
				token = body.split("token=")[1];
				token = token.split("&")[0];
			} catch (Exception e) {
				throw new LoginFailedException();
			}

			AuthInfo.Builder authbuilder = AuthInfo.newBuilder();
			authbuilder.setProvider("ptc");
			authbuilder.setToken(AuthInfo.JWT.newBuilder().setContents(token).setUnknown2(59).build());

			return authbuilder.build();
		} catch (Exception e) {
			e.printStackTrace();
			throw new LoginFailedException();
		}

	}

}
