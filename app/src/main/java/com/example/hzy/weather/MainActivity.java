package com.example.hzy.weather;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class MainActivity extends AppCompatActivity {
    private Button locate, sax, dom, pull, search;
    private EditText city;
    private TextView addressInfo;
    private String weather_url = "http://v.juhe.cn/weather/index";
    private String weather_appKey =  "435c80a596e82f8e5eafebf1ba46d255";
    private String position_url = "http://v.juhe.cn/cell/get";
    private String position_appKey = "b7d4624fc4589ec781cde70f73c650e1";
    private String dtype = "xml";
    private String cityName ;
    private String result;
    private Handler myHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        locate = findViewById(R.id.locate);
        sax = findViewById(R.id.sax);
        dom = findViewById(R.id.dom);
        pull = findViewById(R.id.pull);
        search = findViewById(R.id.search);
        city = findViewById(R.id.city);
        addressInfo = findViewById(R.id.address);
        myHandler = new MyHandler();

        locate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               cityName = city.getText().toString();
               new WeatherThread().start();
            }
        });

        // sax解析
        sax.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    SAXParserFactory spf = SAXParserFactory.newInstance();
                    SAXParser saxParser = spf.newSAXParser();
                    XMLContentHandler xmlContentHandler = new XMLContentHandler();
                    saxParser.parse(new InputSource(new StringReader(result)), xmlContentHandler);
                    String temperature = xmlContentHandler.getTemperature();
                    Toast.makeText(MainActivity.this, "(sax)温度："+temperature, Toast.LENGTH_SHORT).show();
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                } catch (SAXException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // dom解析
        dom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //实例化DocumentBuilderFactory和DocumentBuilder，并创建Document
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = null;
                try {
                    builder = factory.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                }
                try {
                    Document document = builder.parse(new InputSource(new StringReader(result)));
                    //返回文档的根(root)元素
                    Element rootElement =  document.getDocumentElement();
                    NodeList nodes =  rootElement.getElementsByTagName("temperature");
                    Node nTemperature = nodes.item(0).getFirstChild();
                    String temperature = nTemperature.getTextContent();
                    Toast.makeText(MainActivity.this, "(dom)温度："+temperature, Toast.LENGTH_SHORT).show();
                } catch (SAXException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // pull解析
        pull.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                XmlPullParserFactory factory = null;
                try {
                    factory = XmlPullParserFactory.newInstance();
                    factory.setNamespaceAware(true);
                    XmlPullParser xpp = factory.newPullParser();

                    xpp.setInput( new StringReader ( result) );
                    int eventType = xpp.getEventType();
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if(eventType == XmlPullParser.START_DOCUMENT) {
                            System.out.println("Start document");
                        } else if(eventType == XmlPullParser.START_TAG) {
                            if ("temperature".equals(xpp.getName())){
                                Toast.makeText(MainActivity.this, "(pull)温度："+xpp.nextText(), Toast.LENGTH_SHORT).show();
                                break;
                            }
                        } else if(eventType == XmlPullParser.END_TAG) {

                        } else if(eventType == XmlPullParser.TEXT) {

                        }
                        eventType = xpp.next();
                    }
                    System.out.println("End document");
                } catch (XmlPullParserException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // 定位
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                String operator = telephonyManager.getNetworkOperator();
                String mcc = operator.substring(0, 3);
                String mnc = operator.substring(3);
                position_url = position_url+"?mnc="+mnc;
                if(telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA){
                    @SuppressLint("MissingPermission") CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) telephonyManager.getCellLocation();
                    int cid = cdmaCellLocation.getBaseStationId(); //获取cdma基站识别标号 BID
                    int lac = cdmaCellLocation.getNetworkId(); //获取cdma网络编号NID
                    int sid = cdmaCellLocation.getSystemId(); //用谷歌API的话cdma网络的mnc要用这个getSystemId()取得→SID
                }else{
                    @SuppressLint("MissingPermission") GsmCellLocation gsmCellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
                    int cid = gsmCellLocation.getCid(); //获取gsm基站识别标号
                    int lac = gsmCellLocation.getLac(); //获取gsm网络编号
                    position_url = position_url + "&cell=" + cid + "&lac=" + lac;
                }
                position_url = position_url + "&key=" + position_appKey;
                new PositionThread().start();
            }
        });
    }

    public class XMLContentHandler extends DefaultHandler {
        ArrayList<String> temperatures = new ArrayList<>();
        String tagName = null;

        public String getTemperature() {
            return temperatures.get(0);
        }
        //接收文档开始的通知。当遇到文档的开头的时候，调用这个方法，可以在其中做一些预处理。
        @Override
        public void startDocument() throws SAXException {
        }

        //接收元素开始的通知。当读到一个开始标签的时候，会触发这个方法。其中uri表示元素的命名空间；
        //localName表示元素的本地名称（不带前缀）；qName表示元素的限定名（带前缀）；attrs表示元素的属性集合。
        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            if ("temperature".equals(localName)){
                this.tagName = localName;
            }

        }

        //接收字符数据的通知。改方法用来处理在XML文件中读到的内容，第一个参数用来存放文件的内容，后面两个参数
        //是读到的字符串在这个数组中的起始位置和长度。使用newSreing(ch,start,length)就可以获取内容。
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if("temperature".equals(this.tagName)){
                temperatures.add(new String(ch, start, length));
            }
        }

        //接收文档的结尾的通知。在遇到结束标签的时候，调用这个方法。其中，uri表示元素的命名空间；
        //localName表示元素的本地名称（不带前缀）；name表示元素的限定名（带前缀）。
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("temperature".equals(localName)){
                this.tagName = null;
            }
        }
    }
    // handle
    class MyHandler extends Handler {

        // 子类必须重写此方法，接受数据
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            // 此处可以更新UI
            Bundle b = msg.getData();
            result = b.getString("result");
            String address = b.getString("address");
            String sAddressInfo  = b.getString("addressInfo");
            if (result != null){
                Toast.makeText(MainActivity.this, ""+result, Toast.LENGTH_SHORT).show();
            }
            if (address != null && addressInfo != null){
                addressInfo.setText(sAddressInfo);
                city.setText(address);
            }

        }
    }

    //解析字节流
    private String readStream(InputStream is){
        //初始化InputStreamReader、result
        InputStreamReader isr;
        StringBuffer result = new StringBuffer();
        try {
            //读取字符暂存
            String line;
            //字节流转字符流并设置编码为UTF-8
            isr = new InputStreamReader(is,"utf-8");
            //字符流转缓冲流
            BufferedReader br =  new BufferedReader(isr);
            //循环数据存入result
            while((line = br.readLine())!= null){
                result.append(line);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result.toString();
    }

    // 处理天气的Thread
    class WeatherThread extends Thread {

            private String realUrl;
            private String result;

          @Override
          public void run() {
              try {
                  realUrl = weather_url + "?dtype=" + dtype + "&key=" + weather_appKey + "&cityname=" +  URLEncoder.encode(cityName, "UTF-8");
                  URL realUrl1 = new URL(realUrl);
                  HttpURLConnection conn = (HttpURLConnection) realUrl1.openConnection();
                  InputStream is = conn.getInputStream();
                  result = readStream(is);
                  Message msg = new Message();
                  Bundle b = new Bundle();
                  b.putString("result", result);
                  msg.setData(b);
                  myHandler.sendMessage(msg);
              } catch (UnsupportedEncodingException e) {
                  e.printStackTrace();
              } catch (MalformedURLException e) {
                  e.printStackTrace();
              } catch (IOException e) {
                  e.printStackTrace();
              }

          }
      }

    // 处理位置的Thread
    class PositionThread extends Thread{
        Message msg = new Message();
        Bundle b = new Bundle();
        String address = null;
        @Override
        public void run() {
            try {
                URL realUrl = new URL(position_url);
                HttpURLConnection conn = (HttpURLConnection) realUrl.openConnection();
                InputStream is = conn.getInputStream();
                String result1 = readStream(is);
                JSONObject json = new JSONObject(result1);
                JSONObject result2 = json.getJSONObject("result");
                JSONArray data = result2.getJSONArray("data");
                for (int i = 0; i < data.length(); i++){
                    address = data.getJSONObject(i).getString("ADDRESS");
                    b.putString("addressInfo", address);
                }

                Pattern p = Pattern.compile("省.*市");
                Matcher m = p.matcher(address);
                while(m.find()){
                    address = m.group(0).substring(1);
                }

                b.putString("address", address);
                msg.setData(b);
                myHandler.sendMessage(msg);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
