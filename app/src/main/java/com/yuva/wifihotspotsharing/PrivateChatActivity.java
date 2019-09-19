package com.yuva.wifihotspotsharing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.yuva.wifihotspotsharing.filebrowser.FileChooser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

public class PrivateChatActivity extends Activity implements View.OnClickListener {


    static final int SocketServerPORT = 8080;
    private static final int SHARE_PICTURE = 2;
    private static final int REQUEST_PATH = 1;
    private TextView infoPort;
    private EditText edittext;
    private WifiManager wifi;
    private ServerSocket serverSocket = null;
    private ServerSocket fileServerSocket;
    private FileReceiverThread fileReceiverThread;
    private Socket clientSocket;
    private String clientIpAddress;
    private PrintWriter outp = null;
    private BufferedReader inp = null;
    private ArrayList<String> recQue;
    private String[] values;
    private ArrayAdapter adapter;
    private ListView listView;
    private String username;
    private Button connectClientButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat);

        username = (String) getIntent().getExtras().get("name");
        clientIpAddress = (String) getIntent().getExtras().get("ipAddress");


        TextView userNm = (TextView) findViewById(R.id.usrName);
        userNm.setText(username);


        TextView infoIp = (TextView) findViewById(R.id.infoip);
        infoPort = (TextView) findViewById(R.id.infoport);


        listView = (ListView) findViewById(R.id.listView);

        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);


        recQue = new ArrayList<>();

        Button send = (Button) findViewById(R.id.buttonSend);
        send.setOnClickListener(this);
        Button shareButton = (Button) findViewById(R.id.buttonshare);
        shareButton.setOnClickListener(this);

        Button selectButton = (Button) findViewById(R.id.buttonSelect);
        selectButton.setOnClickListener(this);
        edittext = (EditText) findViewById(R.id.editText);

        connectClientButton = (Button) findViewById(R.id.buttonconnectClient);
        connectClientButton.setOnClickListener(this);

        infoIp.setText("Local Address : " + getIpAddress());

        Thread serverThread = new Thread(new chatReceiver());
        serverThread.start();

        fileReceiverThread = new FileReceiverThread();
        fileReceiverThread.start();


    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonSend:
                EditText text = (EditText) findViewById(R.id.editText2);
                String textMsg = text.getText().toString();
                text.setText("");

                sendMessage sendMessage = new sendMessage(textMsg);
                sendMessage.execute((Void) null);

                break;
            case R.id.buttonshare:
                Intent intent = new Intent(PrivateChatActivity.this, FileSharingActivity.class);
                intent.putExtra("username", username);
                startActivityForResult(intent, SHARE_PICTURE);

                break;
            case R.id.buttonSelect:
                Intent intent1 = new Intent(this, FileChooser.class);
                startActivityForResult(intent1, REQUEST_PATH);
                break;

            case R.id.buttonconnectClient:

                Thread startClient = new Thread(new chatSender());
                startClient.start();
                break;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (clientSocket != null) {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {

                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();

                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }
        return ip;
    }

    private void updateUI(final String serverIp, final String clientIp) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                infoPort.setText("Sender: " + serverIp + " | Receiver : " + clientIp);
            }
        });
    }

    private void updateListView(final String message) {

        values = new String[recQue.size()];
        for (int x = 0; x < recQue.size(); x++) {
            values[x] = recQue.get(x);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message + " : " + recQue.size(), Toast.LENGTH_SHORT).show();
                adapter = new ArrayAdapter<>(PrivateChatActivity.this, R.layout.list_white_text, R.id.list_content, values);
                listView.setAdapter(adapter);
                listView.setSelection(adapter.getCount() - 1);
            }
        });
        Log.d("cSharing", "Send : " + message);

    }

    private void updateUIToast(final String message) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PATH) {
            if (resultCode == RESULT_OK) {
                String curFileName = data.getStringExtra("GetFileName");
                String curFilePath = data.getStringExtra("GetPath");
                edittext.setText(curFilePath + curFileName);
            }
        }
    }

    private class chatSender implements Runnable {

        public void run() {
            updateUIToast("creatting socket");

            try {
                clientSocket = new Socket(clientIpAddress, SocketServerPORT + 1);
                clientSocket.setReuseAddress(true);
                //clientSocket.bind(new InetSocketAddress(SocketServerPORT + 1));

                updateUI(getIpAddress(), clientSocket.toString());

                outp = new PrintWriter(clientSocket.getOutputStream(), true);
                inp = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                System.out.println(clientSocket.getInetAddress().getHostAddress() + " is ther server");
                updateUIToast(clientSocket.getInetAddress().getHostAddress() + " is ther server");
            } catch (IOException e) {
                e.printStackTrace();
                updateUIToast(e.toString());

            }

            if (clientSocket != null) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectClientButton.setVisibility(View.GONE);
                    }
                });
            }


        }
    }

    private class chatReceiver implements Runnable {

        public void run() {


            int nreq = 1;
            Socket socket = null;

            try {

                serverSocket = new ServerSocket(SocketServerPORT + 1);
                serverSocket.setReuseAddress(true);
                //serverSocket.bind(new InetSocketAddress(SocketServerPORT + 1));

                final ServerSocket finalServerSocket = serverSocket;
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        infoPort.setText("I'm waiting here: " + finalServerSocket.getLocalPort());
                        updateUIToast("server Created");
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {

                try {
                    assert serverSocket != null;
                    socket = serverSocket.accept();


                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Creating thread ...");

                if (socket != null) {
                    updateUI(getIpAddress(), socket.getInetAddress().getHostName());
                    Thread t = new chatReceiveHandler(socket, nreq);
                    t.start();
                }
            }
        }
    }

    private class chatReceiveHandler extends Thread {
        Socket newSocket;
        int n;

        chatReceiveHandler(Socket s, int v) {
            newSocket = s;
            n = v;
        }


        public void run() {
            try {
                BufferedReader inp = new BufferedReader(new InputStreamReader(newSocket.getInputStream()));
                boolean more_data = true;
                String line;

                while (more_data) {
                    line = inp.readLine();

                    if (line == null) {
                        updateUIToast("line = null");
                        more_data = false;
                    } else {
                        updateUIToast("Message '" + line + "' from " + clientIpAddress);
                        recQue.add(line);
                        updateListView(line);
                    }
                }
                newSocket.close();
                updateUIToast("Disconnected from client number: " + n);
            } catch (Exception e) {
                updateUIToast("IO error " + e);
            }
        }
    }

    public class FileReceiverThread extends Thread {

        @Override
        public void run() {
            Socket socket = null;
            try {
                fileServerSocket = new ServerSocket(SocketServerPORT);
                fileServerSocket.setReuseAddress(true);
                //fileServerSocket.bind(new InetSocketAddress(SocketServerPORT));


                while (true) {
                    socket = fileServerSocket.accept();

                    //---------------------------------
                    FileReceiveHandler fileReceiveHandler = new FileReceiveHandler(socket);
                    fileReceiveHandler.start();
                    //----------------------------------------
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    private class FileReceiveHandler extends Thread {
        Socket socket = null;


        FileReceiveHandler(Socket socket) {
            this.socket = socket;
        }


        @Override
        public void run() {

            File file;
            ObjectInputStream ois;
            ois = null;
            InputStream in = null;
            byte[] bytes;
            FileOutputStream fos = null;


            File theDir = new File(Environment.getExternalStorageDirectory() + "/cSharing");


            // if the directory does not exist, create it
            if (!theDir.exists()) {
                System.out.println("creating directory: " + "cSharing");
                boolean result = false;

                try {
                    theDir.mkdir();
                    result = true;
                } catch (SecurityException ignored) {
                }
                if (result) {
                    System.out.println("DIR created");
                }
            }
            int length = new File(Environment.getExternalStorageDirectory() + "/cSharing").listFiles().length;
            String fileName = "test" + (length + 1) + ".png";


            try {
                in = socket.getInputStream();
            } catch (IOException ex) {
                System.out.println("Can't get socket input stream. ");
            }
            try {
                ois = new ObjectInputStream(in);
            } catch (IOException e1) {
                System.out.println("Can't get Object Input Stream. ");
                e1.printStackTrace();
            }

            try {
                assert ois != null;
                fileName = ois.readUTF();
            } catch (IOException e) {
                System.out.println("Can't get file name. ");
                e.printStackTrace();
            }
            file = new File(Environment.getExternalStorageDirectory() + "/cSharing", fileName);
            try {
                bytes = (byte[]) ois.readObject();
            } catch (ClassNotFoundException | IOException e) {
                System.out.println("Can't read Object . ");
                bytes = new byte[0];
                e.printStackTrace();
            }

            try {
                fos = new FileOutputStream(file);
            } catch (FileNotFoundException e1) {
                System.out.println("Can't get file output stream . ");
                e1.printStackTrace();
            }


            try {
                assert fos != null;
                fos.write(bytes);
                PrivateChatActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(PrivateChatActivity.this, "Finished", Toast.LENGTH_SHORT).show();
                    }
                });
                recQue.add(fileName);
                updateListView(fileName);
            } catch (IOException e1) {
                System.out.println("Can't file output stream write . ");
                e1.printStackTrace();
            } finally {
                if (fos != null) {

                    try {
                        fos.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    private class sendMessage extends AsyncTask<Void, Void, Boolean> {

        String message;

        sendMessage(String message) {

            this.message = username + " : " + message;
        }

        @Override
        protected Boolean doInBackground(Void... params) {

            if (message != null) {
                if (outp != null) {
                    outp.println(message);
                    recQue.add(message);
                    updateUIToast(message);
                    updateListView(message);
                } else {
                    updateUIToast("not connected to user");
                }
            } else {
                updateUI("", "Problem in connection..!");
            }

            return true;
        }
    }


}
