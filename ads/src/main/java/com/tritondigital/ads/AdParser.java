package com.tritondigital.ads;

import android.os.Bundle;
import android.text.*;
import android.util.Xml;

import com.tritondigital.util.Log;
import com.tritondigital.util.XmlPullParserUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;


/**
 * Advertising parser (VAST 3.0 and DAAST 1.0)
 *
 * VAST specs: http://www.iab.net/media/file/VASTv3.0.pdf
 */
class AdParser {
    private Bundle mAd;
    private static final String TAG = Log.makeTag("AdParser");


    /**
     * Parse the provided input stream.
     */
    public Bundle parse(InputStream in) throws XmlPullParserException, IOException {
        try {
            mAd = new Bundle();
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            String elementName = parser.getName();
            if(!TextUtils.isEmpty(elementName))
            {
                mAd.putString(Ad.FORMAT, elementName);
            }
            readDaastOrVast(parser);
        } finally {
            in.close();
        }

        return mAd;
    }


    /**
     * Reads the "DAAST" or "VAST" tag.
     */
    private void readDaastOrVast(XmlPullParser parser) throws XmlPullParserException, IOException {
        String elementName;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                elementName = parser.getName();
                if ((elementName != null) && elementName.equals("Ad")) {
                    readAd(parser);
                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }
    }


    /**
     * Reads the "Ad" tag.
     */
    private void readAd(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Ad");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                if ((elementName != null) && elementName.equals("InLine")) {
                    readInline(parser);
                } else if (elementName.equals("Wrapper")) {
                    readWrapper(parser);
                }
                else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }
    }


    /**
     * Reads the "AdTitle" tag.
     */
    private void readAdTitle(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "AdTitle");
        String title = XmlPullParserUtil.readText(parser);
        mAd.putString(Ad.TITLE, title);
    }


    /**
     * Reads the "Impression" tag.
     */
    private void readImpression(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Impression");

        String url = XmlPullParserUtil.readText(parser);
        if (url == null) {
            return;
        }

        ArrayList<String> urlArrayList = mAd.getStringArrayList(Ad.IMPRESSION_TRACKING_URLS);
        if (urlArrayList == null) {
            urlArrayList = new ArrayList<>();
            mAd.putStringArrayList(Ad.IMPRESSION_TRACKING_URLS, urlArrayList);
        }

        urlArrayList.add(url);
    }


    /**
     * Reads the "Linear" tag.
     */
    private void readLinear(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Linear");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                if (elementName == null) {
                    XmlPullParserUtil.skip(parser);

                } else if (elementName.equals("Duration")) {
                    readDuration(parser);

                } else if (elementName.equals("MediaFiles")) {
                    readMediaFiles(parser);

                } else if (elementName.equals("AudioInteractions") || elementName.equals("VideoClicks")) {
                    readLinearClicks(parser);

                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }
    }


    /**
     * Reads the "Duration" tag.
     */
    private void readDuration(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Duration");
        String duration = XmlPullParserUtil.readText(parser);
        mAd.putString(Ad.DURATION, duration);
    }


    /**
     * Read the "MediaFiles" tag.
     */
    private void readMediaFiles(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "MediaFiles");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                if ((elementName != null) && elementName.equals("MediaFile") && (mAd.getString(Ad.URL) == null)) {
                    readMediaFile(parser);
                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }
    }


    private void readMediaFile(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "MediaFile");

        String mimeType = parser.getAttributeValue(null, "type");
        if (mimeType.startsWith("video")) {
            int width = getIntAttribute(parser, "width");
            int height = getIntAttribute(parser, "height");
            mAd.putInt(Ad.WIDTH, width);
            mAd.putInt(Ad.HEIGHT, height);

        } else if (!mimeType.startsWith("audio")) {
            // Ignore media files that are neither audio of video files.
            Log.w(TAG, "Unsupported MIME type: " + mimeType);
            XmlPullParserUtil.skip(parser);
            return;
        }

        String url = XmlPullParserUtil.readText(parser);
        mAd.putString(Ad.MIME_TYPE, mimeType);
        mAd.putString(Ad.URL,       url);
    }


    private void readLinearClicks(XmlPullParser parser) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                if (elementName == null) {
                    XmlPullParserUtil.skip(parser);

                } else if (elementName.equals("ClickThrough")) {
                    String url = XmlPullParserUtil.readText(parser);
                    mAd.putString(Ad.VIDEO_CLICK_THROUGH_URL, url);

                } else if (elementName.equals("ClickTracking")) {
                    readClickTracking(parser);

                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }
    }


    private void readClickTracking(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "ClickTracking");

        String url = XmlPullParserUtil.readText(parser);
        if (url == null) {
            return;
        }

        ArrayList<String> urlArrayList = mAd.getStringArrayList(Ad.VIDEO_CLICK_TRACKING_URLS);
        if (urlArrayList == null) {
            urlArrayList = new ArrayList<>();
            mAd.putStringArrayList(Ad.VIDEO_CLICK_TRACKING_URLS, urlArrayList);
        }

        urlArrayList.add(url);
    }


    /**
     * Reads the "InLine" tag.
     */
    private void readInline(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "InLine");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {

                String elementName = parser.getName();
                if (elementName == null) {
                    XmlPullParserUtil.skip(parser);

                } else if (elementName.equals("AdTitle")) {
                    readAdTitle(parser);

                } else if (elementName.equals("Impression")) {
                    readImpression(parser);

                } else if (elementName.equals("Creatives")) {
                    readCreatives(parser);

                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }
    }

    /**
     * Read the "Wrapper" tag.
     */
    private void readWrapper(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Wrapper");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {

                String elementName = parser.getName();
                if (elementName == null) {
                    XmlPullParserUtil.skip(parser);

                } else if (elementName.equals("Impression")) {
                    readImpression(parser);

                } else if (elementName.equals("VASTAdTagURI")) {
                    readVastAdTag(parser);

                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }
    }


    /**
     * Reads the "Creatives" tag.
     */
    private void readCreatives(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Creatives");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {

                String elementName = parser.getName();
                if ((elementName != null) && elementName.equals("Creative")) {
                    readCreative(parser);
                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }
    }


    /**
     * Reads the "Creative" tag. The creative tag can contain the "linear ad" or the "companion ads".
     */
    private void readCreative(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Creative");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {

                String elementName = parser.getName();
                if (elementName == null) {
                    XmlPullParserUtil.skip(parser);

                } else if (elementName.equals("Linear")) {
                    readLinear(parser);

                } else if (elementName.equals("CompanionAds")) {
                    ArrayList<Bundle> companionAds = readCompanionAds(parser);
                    if (companionAds != null) {
                        mAd.putParcelableArrayList(Ad.BANNERS, companionAds);
                    }

                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }
    }


    /**
     * Reads the "CompanionAds" tag.
     */
    private ArrayList<Bundle> readCompanionAds(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "CompanionAds");
        ArrayList<Bundle> companionAds = null;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                String elementName = parser.getName();
                if ((elementName != null) && elementName.equals("Companion")) {
                    // Skip the companion parsing if we already have found the right banner.
                    Bundle companion = readCompanion(parser);
                    if (companion != null) {
                        if (companionAds == null) {
                            companionAds = new ArrayList<>();
                        }

                        companionAds.add(companion);
                    }
                } else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }

        return companionAds;
    }


    /**
     * Reads the "Companion" tag.
     *
     * Ignore companions that are not valid ITrameResources.
     */
    private Bundle readCompanion(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "Companion");

        Bundle banner = null;

        // Read size
        int width = getIntAttribute(parser, "width");
        int height = getIntAttribute(parser, "height");

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {

                String elementName = parser.getName();
                if ((elementName != null) && elementName.equals("IFrameResource")) {
                    String url = XmlPullParserUtil.readText(parser);
                    if (url != null) {
                        // Replace the "fmt" parameter so we don't have a margin in the web page.
                        url = url.replace("fmt=iframe", "fmt=htmlpage");

                        banner = new Bundle();
                        banner.putInt(Ad.WIDTH, width);
                        banner.putInt(Ad.HEIGHT, height);
                        banner.putString(Ad.URL, url);
                    }
                }
                else if ((elementName != null) && elementName.equals("HTMLResource")){
                    String html = XmlPullParserUtil.readText(parser);
                    if (html != null) {
                        banner = new Bundle();
                        banner.putInt(Ad.WIDTH, width);
                        banner.putInt(Ad.HEIGHT, height);
                        banner.putString(Ad.HTML, html);
                    }
                }
                else {
                    XmlPullParserUtil.skip(parser);
                }
            }
        }

        return banner;
    }

    /**
     * Reads the "VASTAdTagURI" tag
     */

    private void readVastAdTag(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "VASTAdTagURI");
        String vastAdTag = XmlPullParserUtil.readText(parser);
        mAd.putString(Ad.VAST_AD_TAG, vastAdTag);
    }

    private static int getIntAttribute(XmlPullParser parser, String attribute) {
        String valStr = parser.getAttributeValue(null, attribute);

        try {
            return Integer.parseInt(valStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
