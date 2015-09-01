package barqsoft.footballscores.service;
// cached array of team names/crest to get around
// the http 429 response code
public class TeamCrest {
    String team = null;
    byte[] crest = null;
}
