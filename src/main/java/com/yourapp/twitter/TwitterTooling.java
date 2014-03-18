package com.yourapp.twitter;

import de.jetsli.twitter.AnyExecutor;
import de.jetsli.twitter.Configuration;
import de.jetsli.twitter.Credits;
import de.jetsli.twitter.TwitterSearch;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.User;

/**
 * @author Peter Karich, info@jetsli.de
 */
public class TwitterTooling {

    private static Logger logger = LoggerFactory.getLogger(TwitterTooling.class);
    private final Set<String> ignoreUsers = new LinkedHashSet<String>();
    private final Set<String> ignoreUsersDM = new LinkedHashSet<String>();
    private BufferedWriter writer;
    private BufferedWriter writerDM;
    private int MAX_USERS = 50;

    public static void main(String[] args) throws Exception {
        String ignoreFile = "my-ignore-users.txt";
        TwitterTooling tt = new TwitterTooling(ignoreFile);
        // tt.autoUnfollow();

        // it is crucial to find some accounts with potential interesting users:
        tt.autoFollow("GoogleMapsAPI", false);
//        tt.autoFollow("pannous", false);
//        tt.autoFollow("JetslideApp", false);

        // now the power spamming method: send all your followers a custom direct message!
        // tt.sendAllFollowersDM("Hey USER, we did some nice stuff!"); // USER gets replaced with the user name
    }

    public TwitterTooling(String ignoreUserFile) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(ignoreUserFile));
            String line = null;
            while ((line = reader.readLine()) != null) {
                ignoreUsers.add(line.trim().toLowerCase());
            }
            writer = new BufferedWriter(new FileWriter(ignoreUserFile, true));

            // write for direct message ignore list
            reader = new BufferedReader(new FileReader(ignoreUserFile + ".dm"));
            while ((line = reader.readLine()) != null) {
                ignoreUsersDM.add(line.trim().toLowerCase());
            }
            writerDM = new BufferedWriter(new FileWriter(ignoreUserFile + ".dm", true));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getThisUser(TwitterSearch ts) {
        return ts.getScreenName();
    }
    private int ignored = 0;
    private int alreadyFollowing = 0;

    public void autoFollow(final String userName, boolean getFriends) {
        ignored = 0;
        alreadyFollowing = 0;
        TwitterSearch twitterSearch = createTwitterSearch();
        final Set<String> friendCollection = new LinkedHashSet<String>();
        logger.info("getFriends of this user");
        twitterSearch.getFriends(getThisUser(twitterSearch), new AnyExecutor<User>() {

            @Override
            public User execute(User u) {
                friendCollection.add(u.getScreenName().toLowerCase());
                return u;
            }
        });

        logger.info("getFollowers of " + userName);
        final Map<String, User> users = new LinkedHashMap<String, User>();
        twitterSearch.getFriendsOrFollowers(userName, new AnyExecutor<User>() {

            int seekCounter = 0;

            @Override
            public User execute(User user) {
                float factor = ((float) user.getFollowersCount() / user.getFriendsCount());
                // if factor is too low => probably spammy or something
                // if factor is too high=> probably won't follow back ;)

                if (user.getFollowersCount() > 50 && factor > 0.8 && factor < 6) {
                    String lower = user.getScreenName().toLowerCase();
                    if (friendCollection.contains(lower))
                        alreadyFollowing++;
                    else if (ignoreUsers.contains(lower))
                        ignored++;
                    else {
                        users.put(lower, user);
                        if (users.size() >= MAX_USERS)
                            return null;
                        else if (users.size() % 10 == 0)
                            logger.info("Grabbed " + users.size() + " users ...");
                    }
                }

                if (++seekCounter % 50 == 0)
                    logger.info("Still seeking for followers. User: " + userName
                            + " Counter:" + seekCounter + " collected users:" + users.size());

                return user;
            }
        }, getFriends, true);

        logger.info(ignored + " users ignored, already following:" + alreadyFollowing);

        String friendStr = "friends";
        if (!getFriends)
            friendStr = "followers";

        logger.info("userName:" + userName + " has " + users.size() + " "
                + friendStr + " with a 'high enough score' not too spammy or too hip");

        int newFollowers = 0;
        for (User user : users.values()) {
            try {
//                logger.info(++counter + " " + user.getScreenName() + " follower:"
//                        + user.getFollowersCount() + " foll/friend=" + ((float) user.getFollowersCount() / user.getFriendsCount()));
                twitterSearch.follow(user);
                newFollowers++;

                // now write to ignore list so that when auto or manually 
                // unfollowing them, we still know the visited people
                writer.write(user.getScreenName().toLowerCase().trim() + "\n");
                writer.flush();
            } catch (Exception ex) {
                logger.error("cannot follow: " + user.getScreenName() + " " + TwitterSearch.getMessage(ex));
                continue;
            }
            try {
                Thread.sleep(300L);
            } catch (InterruptedException ex) {
                logger.error("cannot wait!", ex);
                break;
            }
        }
        logger.info(newFollowers + " new followers");
    }

    private void autoUnfollow() throws Exception {
        TwitterSearch twitterSearch = createTwitterSearch();
        Collection<User> users = twitterSearch.getFriendsNotFollowing(getThisUser(twitterSearch));
        System.out.println(twitterSearch.getUser() + " friends not following:" + users.size() + " now auto unfollow. This may take a while ...");
        int success = 0;
        for (User user : users) {
            for (int i = 0; i < 3; i++) {
                try {
                    Thread.sleep(300);
                    twitterSearch.unfollow(user.getScreenName());
                    success++;
                    break;
                } catch (Exception ex) {
                    logger.error("Cannot unfollow:" + user + " " + TwitterSearch.getMessage(ex));
                    continue;
                }
            }
        }
        logger.info("unfollowed:" + success + " of " + users.size() + " users ");
    }

    private TwitterSearch createTwitterSearch() {
        // read into https://dev.twitter.com/apps/1041604 to get access token + secret
        // and the consumer token stuff

        TwitterSearch twitterSearch = new TwitterSearch();
        Credits credits = new Configuration().getTwitterSearchCredits();
        twitterSearch.setConsumer(credits.getConsumerKey(), credits.getConsumerSecret());

        // verify costs us an API point so don't do it!
        // twitterSearch.initTwitter4JInstance(credits.getToken(), credits.getTokenSecret(), true);
        twitterSearch.initTwitter4JInstance(credits.getToken(), credits.getTokenSecret(), false);
        
        if (twitterSearch.getRateLimit() < 1)
            logger.info("minutes until reset:" + twitterSearch.getSecondsUntilReset() / 60f);

        try {
            User u = twitterSearch.getUser();
            logger.info("followers:" + u.getFollowersCount() + " following:" + u.getFriendsCount());
        } catch (Exception ex) {
            logger.error("getTwitterSearch", ex);
        }
        return twitterSearch;
    }

    /**
     * Send automatic direct messages
     */
    public void sendAllFollowersDM(final String string) {
        final TwitterSearch twitterSearch = createTwitterSearch();
        twitterSearch.getFollowers(getThisUser(twitterSearch), new AnyExecutor<User>() {

            int counter = 0;

            @Override
            public User execute(User user) {
                try {
                    String lower = user.getScreenName().toLowerCase().trim();
                    if (ignoreUsersDM.contains(lower)) {
                        logger.info("In ignore list:" + lower);
                        return user;
                    }

                    counter++;
                    String msg = string.replaceAll("USER", user.getName().trim());
                    System.out.println(counter + " " + user.getScreenName() + " " + msg);
                    twitterSearch.sendDMTo(lower, msg);
                    writerDM.write(lower + "\n");
                    writerDM.flush();
                    Thread.sleep(300);
                } catch (Exception ex) {
                    logger.info("Exception:" + TwitterSearch.getMessage(ex));
                }
                return user;
            }
        });
    }
}
