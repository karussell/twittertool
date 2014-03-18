/**
 * Copyright (C) 2010 Peter Karich <info@jetsli.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package de.jetsli.twitter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.IDs;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.RateLimitStatus;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

/**
 * @author Peter Karich, info@jetsli.de
 */
public class TwitterSearch implements Serializable {

    private static final long serialVersionUID = 1L;
    public final static String COOKIE = "jetslide";
    /**
     * Do not use less than this limit of 20 api points for queueing searches of
     * unloggedin users
     */
    public int limit = 1;
    public final static String LINK_FILTER = "filter:links";
    private Twitter twitter;
    protected Logger logger = LoggerFactory.getLogger(TwitterSearch.class);
    private String consumerKey;
    private String consumerSecret;
    private int rateLimit = -1;
    private long lastRateLimitRequest = -1;

    public TwitterSearch() {
    }

    public TwitterSearch setConsumer(String consumerKey, String consumerSecrect) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecrect;
        return this;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    /**
     * Connect with twitter to get a new personalized twitter4j instance.
     *
     * @throws RuntimeException if verification or connecting failed
     */
    public TwitterSearch initTwitter4JInstance(String token, String tokenSecret, boolean verify) {
        if (consumerKey == null)
            throw new NullPointerException("Please use init consumer settings!");

        setupProperties();
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true).
                setOAuthConsumerKey(consumerKey).setOAuthConsumerSecret(consumerSecret).
                setOAuthAccessToken(token).setOAuthAccessTokenSecret(tokenSecret);
        TwitterFactory tf = new TwitterFactory(cb.build());
        twitter = tf.getInstance();
        try {
//            RequestToken requestToken = t.getOAuthRequestToken();
//            System.out.println("TW-URL:" + requestToken.getAuthorizationURL());
            if (verify)
                twitter.verifyCredentials();

            String str = twitter.getScreenName();
            logger.info("create new TwitterSearch for " + str + " with verifification:" + verify);
        } catch (TwitterException ex) {
//            if (checkAndWaitIfRateLimited("initTwitter", ex))
//                return this;

            throw new RuntimeException(ex);
        }
        return this;
    }

    /**
     * Set an already 'connected' twitter4j instance. No exception can be
     * thrown.
     */
    public TwitterSearch setTwitter4JInstance(Twitter tw) {
        twitter = tw;
        return this;
    }

    public Twitter getTwitter4JInstance() {
        return twitter;
    }

    private void setupProperties() {
        // this issue should now be resolved:
        // http://groups.google.com/group/twitter4j/browse_thread/thread/6f6d5b35149e2faa
//        System.setProperty("twitter4j.http.useSSL", "false");

        // friends makes problems
        // http://groups.google.com/group/twitter4j/browse_thread/thread/f696de22d4554143
        // http://groups.google.com/group/twitter-development-talk/browse_thread/thread/cd76f954957f6fb0
        // http://groups.google.com/group/twitter-development-talk/browse_thread/thread/9e9bfec2f076e4f9
        //System.setProperty("twitter4j.http.useSSL", "true");
        // changing some properties to be applied on HttpURLConnection
        // default read timeout 120000 see twitter4j.internal.http.HttpClientImpl
        System.setProperty("twitter4j.http.readTimeout", "10000");

        // default connection time out 20000
        System.setProperty("twitter4j.http.connectionTimeout", "10000");
    }

    /**
     * Opening the url will show you a PIN
     *
     * @throws TwitterException
     */
    public RequestToken doDesktopLogin() throws TwitterException {
        twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(consumerKey, consumerSecret);
        RequestToken requestToken = twitter.getOAuthRequestToken("");
        System.out.println("Open the following URL and grant access to your account:");
        System.out.println(requestToken.getAuthorizationURL());
        return requestToken;

    }

    public AccessToken getToken4Desktop(RequestToken requestToken, String pin) throws TwitterException {
        AccessToken at = twitter.getOAuthAccessToken(requestToken, pin);
        System.out.println("token:" + at.getToken() + " secret:" + at.getTokenSecret());
        return at;
    }
    private RequestToken tmpRequestToken;

    /**
     * @return the url where the user should be redirected to
     */
    public String oAuthLogin(String callbackUrl) throws Exception {
        twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(consumerKey, consumerSecret);
        tmpRequestToken = twitter.getOAuthRequestToken(callbackUrl);
        return tmpRequestToken.getAuthenticationURL();
    }

    /**
     * grab oauth_verifier from request of callback site
     *
     * @return screenname or null
     */
    public AccessToken oAuthOnCallBack(String oauth_verifierParameter) throws TwitterException {
        if (tmpRequestToken == null)
            throw new IllegalStateException("RequestToken is empty. Call oAuthLogin before!");

        AccessToken aToken = twitter.getOAuthAccessToken(tmpRequestToken, oauth_verifierParameter);
        twitter.verifyCredentials();
        tmpRequestToken = null;
        return aToken;
    }

    public String getScreenName() {
        try {
            return twitter.getScreenName();
        } catch (Exception ex) {
            return null;
        }
    }

    public List<Status> getTweets(String screenName) throws TwitterException {
        List<Status> tweets = twitter.getUserTimeline(screenName, new Paging(1, 100));
        rateLimit--;
        return tweets;
    }

    public User getUser() throws TwitterException {
        // limit: 180
        ResponseList<Status> rl = twitter.getUserTimeline();
        return rl.get(0).getUser();
    }

    public User getUser(String screenName) throws TwitterException {
        ResponseList<Status> rl = twitter.getUserTimeline(screenName);
        return rl.get(0).getUser();
    }

    public User getTwitterUser() throws TwitterException {
        ResponseList<User> list = twitter.lookupUsers(new String[]{twitter.getScreenName()});
        rateLimit--;
        if (list.size() == 0)
            return null;
        else if (list.size() == 1)
            return list.get(0);
        else
            throw new IllegalStateException("returned more than one user for screen name:" + twitter.getScreenName());
    }

    RateLimitStatus getRLS() throws TwitterException {
        Map<String, RateLimitStatus> map = twitter.getRateLimitStatus();
        if (map.isEmpty())
            throw new IllegalStateException("no rate limit status available?");

        return map.values().iterator().next();
    }

    public int getSecondsUntilReset() {
        try {
            RateLimitStatus rls = getRLS();
            rateLimit = rls.getRemaining();
            return rls.getSecondsUntilReset();
        } catch (TwitterException ex) {
            logger.error("Cannot determine rate limit:" + ex.getMessage());
            return -1;
        }
    }

    public int getRateLimit() {
        try {
            rateLimit = getRLS().getRemaining();
            return rateLimit;
        } catch (TwitterException ex) {
            logger.error("Cannot determine rate limit", ex);
            return -1;
        }
    }

    public int getRateLimitFromCache() {
        if (twitter == null)
            return -1;

        // refresh regularly
        if ((System.currentTimeMillis() - lastRateLimitRequest) > 1800 * 1000L)
            resetRateLimitCache();

        try {
            if (rateLimit < 0) {
                rateLimit = getRLS().getRemaining();
                lastRateLimitRequest = System.currentTimeMillis();
            }
        } catch (TwitterException ex) {
            resetRateLimitCache();
        }

        return rateLimit;
    }

    /**
     * forces correct rate limit for next getRateLimitFromCache
     */
    public void resetRateLimitCache() {
        rateLimit = -1;
        lastRateLimitRequest = -1;
    }

    public Status getTweet(long id) throws TwitterException {
        Status st = twitter.showStatus(id);
        rateLimit--;
        return st;
    }

    public void getTest() throws TwitterException {
        System.out.println(twitter.getFollowersIDs("dzone", 0).getIDs());
        System.out.println(twitter.getFriendsIDs("dzone", 0));
        rateLimit -= 2;
    }

    // this works:
    // curl -u user:pw http://api.twitter.com/1/statuses/13221113653/retweeted_by.xml => Peter
    // curl -u user:pw http://api.twitter.com/1/statuses/13221113653/retweeted_by/ids.xml => 51798603 (my user id)
    List<Status> getRetweets(long id) {
        return Collections.EMPTY_LIST;
//        try {
//            return twitter.getRetweets(id);
//        } catch (TwitterException ex) {
//            throw new RuntimeException(ex);
//        }
    }
    private long lastAccess = 0;

//    public List<TweetEntity> getSomeTweets() {
//        if ((System.currentTimeMillis() - lastAccess) < 50 * 1000) {
//            logger.info("skipping public timeline");
//            return Collections.emptyList();
//        }
//
//        lastAccess = System.currentTimeMillis();
//        List<TweetEntity> res = new ArrayList<TweetEntity>();
//        try {
//            ResponseList<Status> statusList = twitter.getPublicTimeline();
//            rateLimit--;
//            for (Status st : statusList) {
//                res.add(toTweet(st));
//            }
//            return res;
//        } catch (TwitterException ex) {
//            logger.error("Cannot get trends!", ex);
//            return res;
//        }
//    }
//    public static Twitter4JTweet toTweet(Status st) {
//        return toTweet(st, st.getUser());
//    }
//
//    public static Twitter4JTweet toTweet(Status st, User user) {
//        if (user == null)
//            throw new IllegalArgumentException("User mustn't be null!");
//        if (st == null)
//            throw new IllegalArgumentException("Status mustn't be null!");
//
//        Twitter4JTweet tw = new Twitter4JTweet(st.getId(), st.getText(), user.getScreenName());
//        tw.setCreatedAt(st.getCreatedAt());
//        tw.setFromUser(user.getScreenName());
//
//        if (user.getProfileImageURL() != null)
//            tw.setProfileImageUrl(user.getProfileImageURL().toString());
//
//        tw.setSource(st.getSource());
//        tw.setToUser(st.getInReplyToUserId(), st.getInReplyToScreenName());
//        tw.setInReplyToStatusId(st.getInReplyToStatusId());
//
//        if (st.getGeoLocation() != null) {
//            tw.setGeoLocation(st.getGeoLocation());
//            tw.setLocation(st.getGeoLocation().getLatitude() + ", " + st.getGeoLocation().getLongitude());
//        } else if (st.getPlace() != null)
//            tw.setLocation(st.getPlace().getCountryCode());
//        else if (user.getLocation() != null)
//            tw.setLocation(toStandardLocation(user.getLocation()));
//
//        return tw;
//    }
    public static String toStandardLocation(String loc) {
        if (loc == null || loc.trim().length() == 0)
            return null;

        String[] locs;
        if (loc.contains("/"))
            locs = loc.split("/", 2);
        else if (loc.contains(","))
            locs = loc.split(",", 2);
        else
            locs = new String[]{loc};

        if (locs.length == 2)
            return locs[0].replaceAll("[,/]", " ").replaceAll("  ", " ").trim() + ", "
                    + locs[1].replaceAll("[,/]", " ").replaceAll("  ", " ").trim();
        else
            return locs[0].replaceAll("[,/]", " ").replaceAll("  ", " ").trim() + ", -";
    }

    Query createQuery(String str) {
        Query q = new Query(str);
        q.setResultType(Query.RECENT);
        return q;
    }

    /**
     * API COSTS: 1
     *
     * @param users should be maximal 100 users
     * @return the latest tweets of the users
     */
    public Collection<? extends Status> updateUserInfo(List<? extends User> users) {
        int counter = 0;
        String arr[] = new String[users.size()];

        // responseList of twitter.lookup has not the same order as arr has!!
        Map<String, User> userMap = new LinkedHashMap<String, User>();

        for (User u : users) {
            arr[counter++] = u.getScreenName();
            userMap.put(u.getScreenName(), u);
        }

        int maxRetries = 5;
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                ResponseList<User> res = twitter.lookupUsers(arr);
                rateLimit--;
                List<Status> tweets = new ArrayList<Status>();
                for (int ii = 0; ii < res.size(); ii++) {
                    User user = res.get(ii);
                    User yUser = userMap.get(user.getScreenName().toLowerCase());
                    if (yUser == null)
                        continue;

//                    Status stat = yUser.updateFieldsBy(user);
//                    if (stat == null)
//                        continue;
//                    Status tw = toTweet(stat, res.get(ii));
                    tweets.add(user.getStatus());
                }
                return tweets;
            } catch (TwitterException ex) {
                logger.warn("Couldn't lookup users. Retry:" + retry + " of " + maxRetries, ex);
                if (retry < 1)
                    continue;
                else
                    break;
            }
        }

        return Collections.EMPTY_LIST;
    }

    public void getFollowers(String user, AnyExecutor<User> anyExecutor) {
        getFriendsOrFollowers(user, anyExecutor, false, false);
    }

    public void getFollowers(String user, AnyExecutor<User> anyExecutor, boolean waitIfNoApiPoints) {
        getFriendsOrFollowers(user, anyExecutor, false, waitIfNoApiPoints);
    }

    public void getFriends(String userName, AnyExecutor<User> executor) {
        getFriendsOrFollowers(userName, executor, true, false);
    }

    public void getFriends(String userName, AnyExecutor<User> executor, boolean waitIfNoApiPoints) {
        getFriendsOrFollowers(userName, executor, true, waitIfNoApiPoints);
    }

    public void getFriendsOrFollowers(String userName, AnyExecutor<User> executor, boolean friends, boolean waitIfNoApiPoints) {
        long cursor = -1;
        resetRateLimitCache();
        MAIN:
        while (true) {
            if (waitIfNoApiPoints) {
                checkAndWaitIfRateLimited("getFriendsOrFollowers 1");
            }

            ResponseList<User> res = null;
            IDs ids = null;
            try {
                if (friends)
                    ids = twitter.getFriendsIDs(userName, cursor);
                else
                    ids = twitter.getFollowersIDs(userName, cursor);

                rateLimit--;
            } catch (TwitterException ex) {
                if (waitIfNoApiPoints && checkAndWaitIfRateLimited("getFriendsOrFollowers 2", ex)) {
                    // nothing to do
                } else
                    logger.warn(ex.getMessage());
                break;
            }
            if (ids.getIDs().length == 0)
                break;

            long[] intids = ids.getIDs();

            // split into max 100 batch            
            for (int offset = 0; offset < intids.length; offset += 100) {
                long[] limitedIds = new long[100];
                for (int ii = 0; ii + offset < intids.length && ii < limitedIds.length; ii++) {
                    limitedIds[ii] = intids[ii + offset];
                }

                // retry at max N times for every id bunch
                for (int trial = 0; trial < 5; trial++) {
                    try {
                        res = twitter.lookupUsers(limitedIds);
                    } catch (TwitterException ex) {
                        if (waitIfNoApiPoints && checkAndWaitIfRateLimited("getFriendsOrFollowers 3 (trial " + trial + ")", ex)) {
                            // nothing to do
                        } else {
                            // now hoping that twitter recovers some seconds later
                            logger.error("(trial " + trial + ") error while lookupUsers: " + getMessage(ex));
                            myWait(10);
                        }
                        continue;
                    } catch (Exception ex) {
                        logger.error("(trial " + trial + ") error while lookupUsers: " + getMessage(ex));
                        myWait(5);
                        continue;
                    }

                    rateLimit--;
                    for (User user : res) {
                        // strange that this was necessary for ibood
                        if (user.getScreenName().trim().isEmpty())
                            continue;

                        if (executor.execute(user) == null)
                            break MAIN;
                    }
                    break;
                }

                if (res == null) {
                    logger.error("giving up");
                    break;
                }
            }

            if (!ids.hasNext())
                break;

            cursor = ids.getNextCursor();
        }
    }

    public boolean checkAndWaitIfRateLimited(String msg) {
        return checkAndWaitIfRateLimited(msg, null);
    }

    public boolean checkAndWaitIfRateLimited(String msg, Exception ex) {
        if (isRateLimitProblem(ex)) {
            waitRateLimited(msg);
            return true;
        }
        return false;
    }

    public void waitRateLimited(String msg) {
        logger.warn(msg + " rate limited!");
        int secs = getSecondsUntilReset();
        if (secs > 1) {
            logger.warn("... now waiting " + secs + " secs");
            myWait(secs);
        }
        resetRateLimitCache();
    }

    public boolean isRateLimited() {
        return getRateLimitFromCache() < limit;
    }

    public TwitterSearch setLimit(int limit) {
        this.limit = limit;
        return this;
    }

    public Collection<User> getFriendsNotFollowing(String user) {
        final Set<User> tmpUsers = new LinkedHashSet<User>();
        AnyExecutor exec = new AnyExecutor<User>() {

            @Override
            public User execute(User o) {
                tmpUsers.add(o);
                return o;
            }
        };
        getFriendsOrFollowers(user, exec, true, false);

        // store friends (people who are followed from specified user)
        Set<User> friends = new LinkedHashSet<User>(tmpUsers);
        System.out.println("friends:" + friends.size());

        // store followers of specified user into tmpUsers
        tmpUsers.clear();
        getFriendsOrFollowers(user, exec, false, false);
        System.out.println("followers:" + tmpUsers.size());

        // now remove users from friends which already follow
        for (User u : tmpUsers) {
            friends.remove(u);
        }
        return friends;
    }

    public void unfollow(String user) {
        try {
            twitter.destroyFriendship(user);
        } catch (TwitterException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void follow(User user) {
        try {
            twitter.createFriendship(user.getScreenName());
        } catch (TwitterException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Status doRetweet(long twitterId) throws TwitterException {
        Status st = twitter.retweetStatus(twitterId);
        rateLimit--;
        return st;
    }

    private void myWait(float seconds) {
        try {
            Thread.sleep(Math.round(seconds * 1000));
        } catch (Exception ex) {
            throw new UnsupportedOperationException(ex);
        }
    }

    /**
     * @return a message describing the problem with twitter or an empty string
     * if nothing related to twitter!
     */
    public static String getMessage(Exception ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        if (msg.length() > 100)
            msg = msg.substring(0, 100);
        if (ex instanceof TwitterException) {
            TwitterException twExc = (TwitterException) ex;
            if (twExc.exceededRateLimitation())
                return "Couldn't process your request. You don't have enough twitter API points!"
                        + " Please wait: " + twExc.getRetryAfter() + " seconds and try again! " + msg;
            else if (twExc.isCausedByNetworkIssue())
                return "Couldn't process your request. Network issue." + msg;

            return "Couldn't process your request. Something went wrong while communicating with Twitter :-/ " + msg;
        } else if (ex != null)
            return msg;

        return "";
    }

    public boolean isInitialized() {
        return twitter != null;
    }

    public void sendDMTo(String screenName, String txt) throws TwitterException {
        twitter.sendDirectMessage(screenName, txt);
    }

    public boolean isAuthenticationProblem(Exception ex) {
        return ex != null && ex.getMessage().startsWith("401:Authentication credentials");
    }

    public boolean isTwitterOverloadProblem(Exception ex) {
        return ex != null && ex.getMessage().startsWith("502:Twitter is down or being upgraded");
    }

    public boolean isRateLimitProblem(Exception ex) {
        return ex != null && ex.getMessage().startsWith("400:The request was invalid. An accompanying error message will explain why.")
                || ex != null && ex instanceof TwitterException && ((TwitterException) ex).exceededRateLimitation()
                || isRateLimited();
    }

    // Twitter Search API:
    // Returns up to a max of roughly 1500 results
    // Rate limited by IP address.
    // The specific number of requests a client is able to make to the Search API for a given hour is not released.
    // The number is quite a bit higher and we feel it is both liberal and sufficient for most applications.
    // The since_id parameter will be removed from the next_page element as it is not supported for pagination.
    public long search(String term, Collection<Status> result, int tweets, long lastMaxCreateTime) throws TwitterException {
        Map<String, User> userMap = new LinkedHashMap<String, User>();
        return search(term, result, userMap, tweets, lastMaxCreateTime);
    }

    long search(String term, Collection<Status> result,
            Map<String, User> userMap, int tweets, long lastMaxCreateTime) throws TwitterException {
        long maxId = 0L;
        long maxMillis = 0L;
        // TODO it looks like only one page is possible with 4.0.0
        int maxPages = 1;
        int hitsPerPage = tweets;

        boolean breakPaging = false;
        for (int page = 0; page < maxPages; page++) {
            Query query = new Query(term);
            // RECENT or POPULAR
            query.setResultType(Query.MIXED);

            // avoid that more recent results disturb our paging!
            if (page > 0)
                query.setMaxId(maxId);

            query.setCount(hitsPerPage);
            QueryResult res = twitter.search(query);

            // is res.getTweets() sorted?
            for (Status twe : res.getTweets()) {
                // determine maxId in the first page
                if (page == 0 && maxId < twe.getId())
                    maxId = twe.getId();

                if (maxMillis < twe.getCreatedAt().getTime())
                    maxMillis = twe.getCreatedAt().getTime();

                if (twe.getCreatedAt().getTime() + 1000 < lastMaxCreateTime)
                    breakPaging = true;
                else {
                    String userName = twe.getUser().getScreenName().toLowerCase();
                    User user = userMap.get(userName);
                    if (user == null)
                        userMap.put(userName, twe.getUser());

                    result.add(twe);
                }
            }

            // minMillis could force us to leave earlier than defined by maxPages
            // or if resulting tweets are less then request (but -10 because of twitter strangeness)
            if (breakPaging || res.getTweets().size() < hitsPerPage - 10)
                break;
        }

        return maxMillis;
    }
}
