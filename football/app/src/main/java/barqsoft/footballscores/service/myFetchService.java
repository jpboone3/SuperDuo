package barqsoft.footballscores.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.Vector;

import barqsoft.footballscores.DatabaseContract;
import barqsoft.footballscores.R;
import barqsoft.footballscores.WidgetProvider;

/**
 * Created by yehya khaled on 3/2/2015.
 */
public class myFetchService extends IntentService {
    public static final String LOG_TAG = "myFetchService";
    private static ArrayList mTeamArray = new ArrayList();
    private ArrayList mNextMatches = null;
    private int m_HTTPResponseCode = 0;
    private int[] allWidgetIds = null;
    private AppWidgetManager appWidgetManager = null;

    public myFetchService() {
        super("myFetchService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        appWidgetManager = AppWidgetManager.getInstance(this
                .getApplicationContext());
        allWidgetIds = intent
                .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);

        for (int i = 0; i < 5; i++) {
            // initial didget list view
            if (allWidgetIds != null)
                mNextMatches = new ArrayList();
            else
                mNextMatches = null;

            // load current and future matches
            getData("n2");

            if (mNextMatches != null) {
                StringBuffer sb = new StringBuffer();
                sb.append(getResources().getText(R.string.upcoming));
                sb.append("\n");
                for (int j = 0; j < 10; j++) {
                    if (mNextMatches.size() <= j)
                        break;
                    String s = (String) mNextMatches.get(j);
                    sb.append(s);

                }
                // update from AppWidget
                for (int widgetId : allWidgetIds) {
                    // create some random data
                    int number = (new Random().nextInt(100));

                    RemoteViews remoteViews = new RemoteViews(this
                            .getApplicationContext().getPackageName(),
                            R.layout.widget);
                    // Set the text
                    remoteViews.setTextViewText(R.id.update, sb.toString());
                    // Register an onClickListener
                    Intent clickIntent = new Intent(this.getApplicationContext(),
                            WidgetProvider.class);

                    clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                    clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
                            allWidgetIds);

                    PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, clickIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                    remoteViews.setOnClickPendingIntent(R.id.update, pendingIntent);
                    appWidgetManager.updateAppWidget(widgetId, remoteViews);
                }
            }
            mNextMatches = null;

            // load past matche outcomes
            if (m_HTTPResponseCode != 429) {
                getData("p2");
            }
            /*
            if we get a HTTP 429 - too many requests, wait
            60 seconds and try again. Note the Todays teams will show
            properly while this service is getting all of the team images
            */

            if (m_HTTPResponseCode == 429) {
                SystemClock.sleep(60000);
            } else
                break;
        }
        return;
    }

    private void getData(String timeFrame) {
        //Creating fetch URL
        final String BASE_URL = "http://api.football-data.org/alpha/fixtures"; //Base URL
        final String QUERY_TIME_FRAME = "timeFrame"; //Time Frame parameter to determine days
        //final String QUERY_MATCH_DAY = "matchday";

        Uri fetch_build = Uri.parse(BASE_URL).buildUpon().
                appendQueryParameter(QUERY_TIME_FRAME, timeFrame).build();
        //Log.d(LOG_TAG, "The url we are looking at is: "+fetch_build.toString()); //log spam
        HttpURLConnection m_connection = null;
        BufferedReader reader = null;
        String JSON_data = null;
        //Opening Connection
        try {
            URL fetch = new URL(fetch_build.toString());
            m_connection = (HttpURLConnection) fetch.openConnection();
            m_connection.setRequestMethod("GET");
            m_connection.addRequestProperty("X-Auth-Token", getString(R.string.api_key));
            m_connection.connect();
            m_HTTPResponseCode = m_connection.getResponseCode();

            if (m_HTTPResponseCode == 200) {

                // Read the input stream into a String
                InputStream inputStream = m_connection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }
                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return;
                }
                JSON_data = buffer.toString();
                m_connection.disconnect();
                m_connection = null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception here: " + e.getMessage());
            Log.e(LOG_TAG, "Exception here: " + e.toString());
        } finally {
            if (m_connection != null) {
                m_connection.disconnect();
                m_connection = null;
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Error Closing Stream");
                }
            }
        }
        try {
            if (JSON_data != null) {
                //This bit is to check if the data contains any matches. If not, we call processJson on the dummy data
                JSONArray matches = new JSONObject(JSON_data).getJSONArray("fixtures");
                if (matches.length() == 0) {
                    //if there is no data, call the function on dummy data
                    //this is expected behavior during the off season.
                    processJSONdata(getString(R.string.dummy_data), getApplicationContext(), false);
                    return;
                }


                processJSONdata(JSON_data, getApplicationContext(), true);
            } else {
                //Could not Connect
                Log.d(LOG_TAG, "Could not connect to server.");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    private void processJSONdata(String JSONdata, Context mContext, boolean isReal) {
        //Log.d(LOG_TAG,JSONdata);
        //JSON data
        // This set of league codes is for the 2015/2016 season. In fall of 2016, they will need to
        // be updated. Feel free to use the codes
        final String BUNDESLIGA1 = "394";
        final String BUNDESLIGA2 = "395";
        final String LIGUE1 = "396";
        final String LIGUE2 = "397";
        final String PREMIER_LEAGUE = "398";
        final String PRIMERA_DIVISION = "399";
        final String SEGUNDA_DIVISION = "400";
        final String SERIE_A = "401";
        final String PRIMERA_LIGA = "402";
        final String Bundesliga3 = "403";
        final String EREDIVISIE = "404";


        final String SEASON_LINK = "http://api.football-data.org/alpha/soccerseasons/";
        final String MATCH_LINK = "http://api.football-data.org/alpha/fixtures/";
        final String FIXTURES = "fixtures";
        final String LINKS = "_links";
        final String SOCCER_SEASON = "soccerseason";
        final String SELF = "self";
        final String HOMETEAM = "homeTeam";
        final String AWAYTEAM = "awayTeam";
        final String MATCH_DATE = "date";
        final String HOME_TEAM = "homeTeamName";
        final String AWAY_TEAM = "awayTeamName";
        final String RESULT = "result";
        final String HOME_GOALS = "goalsHomeTeam";
        final String AWAY_GOALS = "goalsAwayTeam";
        final String MATCH_DAY = "matchday";

        //Match data
        String League = null;
        String mDate = null;
        String mTime = null;
        String Home = null;
        String Away = null;
        String Home_goals = null;
        String Away_goals = null;
        String match_id = null;
        String match_day = null;
        String home_team = null;
        String away_team = null;


        try {
            JSONArray matches = new JSONObject(JSONdata).getJSONArray(FIXTURES);

            //ContentValues to be inserted
            Vector<ContentValues> values = new Vector<ContentValues>(matches.length());
            for (int i = 0; i < matches.length(); i++) {

                JSONObject match_data = matches.getJSONObject(i);
                League = match_data.getJSONObject(LINKS).getJSONObject(SOCCER_SEASON).
                        getString("href");
                League = League.replace(SEASON_LINK, "");
                //This if statement controls which leagues we're interested in the data from.
                //add leagues here in order to have them be added to the DB.
                // If you are finding no data in the app, check that this contains all the leagues.
                // If it doesn't, that can cause an empty DB, bypassing the dummy data routine.

                if (League.equals(PREMIER_LEAGUE) ||
                        League.equals(SERIE_A) ||
                        League.equals(Bundesliga3) ||
                        League.equals(BUNDESLIGA1) ||
                        League.equals(BUNDESLIGA2) ||
                        League.equals(PRIMERA_DIVISION)) {
                    home_team = match_data.getJSONObject(LINKS).getJSONObject(HOMETEAM).
                            getString("href");
                    away_team = match_data.getJSONObject(LINKS).getJSONObject(AWAYTEAM).
                            getString("href");

                    byte[] home_crest = null;
                    byte[] away_crest = null;
                    //Log.d(LOG_TAG, "Last HTTP home: " + m_HTTPResponseCode);

                    if (m_HTTPResponseCode != 429) {
                        home_crest = getTeamCrest(home_team);
                    }
                    //Log.d(LOG_TAG, "Last HTTP away: " + m_HTTPResponseCode);
                    if (m_HTTPResponseCode != 429) {
                        away_crest = getTeamCrest(away_team);
                    }

                    match_id = match_data.getJSONObject(LINKS).getJSONObject(SELF).
                            getString("href");
                    match_id = match_id.replace(MATCH_LINK, "");
                    if (!isReal) {
                        //This if statement changes the match ID of the dummy data so that it all goes into the database
                        match_id = match_id + Integer.toString(i);
                    }

                    mDate = match_data.getString(MATCH_DATE);
                    mTime = mDate.substring(mDate.indexOf("T") + 1, mDate.indexOf("Z"));
                    mDate = mDate.substring(0, mDate.indexOf("T"));
                    SimpleDateFormat match_date = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss");
                    match_date.setTimeZone(TimeZone.getTimeZone("UTC"));
                    try {
                        Date parseddate = match_date.parse(mDate + mTime);
                        SimpleDateFormat new_date = new SimpleDateFormat("yyyy-MM-dd:HH:mm");
                        new_date.setTimeZone(TimeZone.getDefault());
                        mDate = new_date.format(parseddate);
                        mTime = mDate.substring(mDate.indexOf(":") + 1);
                        mDate = mDate.substring(0, mDate.indexOf(":"));

                        if (!isReal) {
                            //This if statement changes the dummy data's date to match our current date range.
                            Date fragmentdate = new Date(System.currentTimeMillis() + ((i - 2) * 86400000));
                            SimpleDateFormat mformat = new SimpleDateFormat("yyyy-MM-dd");
                            mDate = mformat.format(fragmentdate);
                        }
                    } catch (Exception e) {
                        Log.d(LOG_TAG, "error here!");
                        Log.e(LOG_TAG, e.getMessage());
                    }
                    Home = match_data.getString(HOME_TEAM);
                    Away = match_data.getString(AWAY_TEAM);
                    Home_goals = match_data.getJSONObject(RESULT).getString(HOME_GOALS);
                    Away_goals = match_data.getJSONObject(RESULT).getString(AWAY_GOALS);
                    match_day = match_data.getString(MATCH_DAY);
                    ContentValues match_values = new ContentValues();
                    match_values.put(DatabaseContract.scores_table.MATCH_ID, match_id);
                    match_values.put(DatabaseContract.scores_table.DATE_COL, mDate);
                    match_values.put(DatabaseContract.scores_table.TIME_COL, mTime);
                    match_values.put(DatabaseContract.scores_table.HOME_COL, Home);
                    match_values.put(DatabaseContract.scores_table.AWAY_COL, Away);
                    match_values.put(DatabaseContract.scores_table.HOME_GOALS_COL, Home_goals);
                    match_values.put(DatabaseContract.scores_table.AWAY_GOALS_COL, Away_goals);
                    match_values.put(DatabaseContract.scores_table.LEAGUE_COL, League);
                    match_values.put(DatabaseContract.scores_table.MATCH_DAY, match_day);
                    if (home_crest != null)
                        match_values.put(DatabaseContract.scores_table.HOME_CREST, home_crest);
                    if (away_crest != null)
                        match_values.put(DatabaseContract.scores_table.AWAY_CREST, away_crest);
                    //log spam

                    //Log.d(LOG_TAG,match_id);
                    //Log.d(LOG_TAG,mDate);
                    //Log.d(LOG_TAG,mTime);
                    //Log.d(LOG_TAG,Home);
                    //Log.d(LOG_TAG,Away);
                    //Log.d(LOG_TAG,Home_goals);
                    //Log.d(LOG_TAG,Away_goals);

                    values.add(match_values);
                    /* add to array list of upcoming
                       matches for the app allWidgetIds
                    */
                    if (mNextMatches != null) {
                        String mS1 = mDate.substring(5, 7) + "/" + mDate.substring(8) + " " + mTime + " ";
                        if (Home_goals.equals("-1"))
                            mS1 += "( - )";
                        else
                            mS1 += "(" + Home_goals + " - " + Away_goals + ")";
                        mS1 += " " + Home + " " +
                                getResources().getText(R.string.vs)
                                + " " + Away + "\n";
                        mNextMatches.add(mS1);
                    }
                }
            }
            int inserted_data = 0;
            ContentValues[] insert_data = new ContentValues[values.size()];
            values.toArray(insert_data);
            inserted_data = mContext.getContentResolver().bulkInsert(
                    DatabaseContract.BASE_CONTENT_URI, insert_data);

            //Log.d(LOG_TAG,"Succesfully Inserted : " + String.valueOf(inserted_data));
        } catch (JSONException e) {
            Log.e(LOG_TAG, "JSON E: " + e.getMessage());
        }

    }

    private byte[] getTeamCrest(String team) {
        if (team == null)
            return null;
        team = team.replaceFirst("alpha/", "");
        // see if we have cached the team/crest
        for (int i = 0; i < mTeamArray.size(); i++) {
            TeamCrest tc = (TeamCrest) mTeamArray.get(i);
            if (tc.team.equals(team)) {
                return tc.crest;
            }
        }
        HttpURLConnection m_connection = null;
        BufferedReader reader = null;
        String JSON_data = null;
        String crest_url = null;
        byte[] crest = null;
        //Opening Connection

        try {
            URL fetch = new URL(team);
            m_connection = (HttpURLConnection) fetch.openConnection();
            m_connection.setRequestMethod("GET");
            m_connection.addRequestProperty("X-Auth-Token", getString(R.string.api_key));
            m_connection.connect();
            m_HTTPResponseCode = m_connection.getResponseCode();

            if (m_HTTPResponseCode == 200) {

                // Read the input stream into a String
                InputStream inputStream = m_connection.getInputStream();


                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }
                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                JSON_data = buffer.toString();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception Url: " + e.getMessage());
            Log.e(LOG_TAG, "Exception Url: " + e.toString());
        } finally {
            if (m_connection != null) {
                m_connection.disconnect();
                m_connection = null;
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Error Closing Stream: " + e.toString());
                }
            }
        }
        if (JSON_data == null || JSON_data.length() == 0)
            return null;

        try {
            //JSONArray teamData = new JSONObject(JSON_data)
            crest_url = new JSONObject(JSON_data).getString("crestUrl");
            if (crest_url != null) {
                if (crest_url.length() == 0)
                    crest_url = null;
                else if (crest_url.equals("none.jpg"))
                    crest_url = null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception JSON: " + e.getMessage());
        }
        if (crest_url == null)
            return null;
        crest_url = crest_url.replaceFirst("http:", "https:");
        if (crest_url.startsWith("https://") == false)
            crest_url = "https://" + crest_url;

        if (crest_url != null) {
            InputStream inputStream = null;

            try {
                URL url = new URL(crest_url);
                m_connection = (HttpURLConnection) url.openConnection();
                m_connection.setRequestMethod("GET");
                m_connection.addRequestProperty("X-Auth-Token", getString(R.string.api_key));
                m_connection.connect();
                m_HTTPResponseCode = m_connection.getResponseCode();

                if (m_HTTPResponseCode == 200) {

                    inputStream = m_connection.getInputStream();
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    byte[] buf = new byte[512];
                    while (true) {
                        int len = inputStream.read(buf);
                        //Log.d(LOG_TAG, "baos length: wrote " + len + " bytes");
                        if (len == -1) {
                            break;
                        }
                        stream.write(buf, 0, len);
                    }

                    crest = stream.toByteArray();

                    if (crest != null) {
                        //Log.d(LOG_TAG, "Crest length: " + crest.length);
                        if (crest.length == 0)
                            crest = null;
                    }
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage(), e);
            } finally {
                if (m_connection != null) {
                    m_connection.disconnect();
                    m_connection = null;
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Error Closing Stream");
                    }
                }
            }
        }
        if (m_HTTPResponseCode == 200 ||
                m_HTTPResponseCode == 404) {
            /*
             * cache the team/crest because of a possible HTTP 429 errors
             * (too many requests) from the server.
            */
            TeamCrest mTeamCrest = new TeamCrest();
            mTeamCrest.team = team;
            mTeamCrest.crest = crest;
            mTeamArray.add(mTeamCrest);
        }
        return crest;

    }

}