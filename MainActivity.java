package com.apposite.wifip2p;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener{

    WifiP2pManager wifi;
    private WifiP2pInfo info;
    ListView listViewPeer;
    WifiP2pManager.Channel channel;
    IntentFilter intentFilter = new IntentFilter();
    Button discover, send, receive;
    List<WifiP2pDevice> peersList;
    List<String> peersNameList;
    BroadcastReceiver receiver = null;
    ProgressDialog progressDialogDiscover = null, progressDialogConnect = null;
    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    Intent serviceIntent;
    final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE=0;
    boolean PERMISSIONS_FLAG = false, SERVICE_ON = false, isHost = false, isConnected = false;
    String FILE_EXTENSION = "jpg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);

        listViewPeer = (ListView) findViewById(R.id.ListViewPeer);
        discover = (Button) findViewById(R.id.btnDiscover);
        send = (Button) findViewById(R.id.btnGallery);
        receive = (Button) findViewById(R.id.btnReceive);

        wifi = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = wifi.initialize(this, getMainLooper(), null);
        receiver = new WifiP2pBroadcastReceiver(wifi, channel, this);

        peersList = new ArrayList<>();
        peersNameList = new ArrayList<>();

        discover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (progressDialogDiscover != null && progressDialogDiscover.isShowing()) {
                    progressDialogDiscover.dismiss();
                }
                progressDialogDiscover = ProgressDialog.show(MainActivity.this, "Press back to cancel", "finding peers", false,
                        true, new DialogInterface.OnCancelListener() {

                            @Override
                            public void onCancel(DialogInterface dialog) {
                                if(peersList.size()==0)
                                    Toast.makeText(MainActivity.this, "Peer Not Found.", Toast.LENGTH_SHORT).show();
                            }
                        });

                Runnable progressRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (progressDialogDiscover != null && progressDialogDiscover.isShowing()) {
                            progressDialogDiscover.dismiss();
                        }
                    }
                };
                Handler pdCanceller = new Handler();
                pdCanceller.postDelayed(progressRunnable, 15000);

                wifi.discoverPeers(channel, new WifiP2pManager.ActionListener(){
                    @Override
                    public void onSuccess() {

                    }

                    @Override
                    public void onFailure(int reason) {
                        Toast.makeText(MainActivity.this, "Peer Discovery failed.", Toast.LENGTH_SHORT).show();
                        if (progressDialogDiscover != null && progressDialogDiscover.isShowing()) {
                            progressDialogDiscover.dismiss();
                        }
                    }
                });
            }
        });

        refreshList();

        listViewPeer.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(!isConnected)
                    connect(position);
                else
                    disconnect();
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
            }
        });

        receive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isHost){
                    Toast.makeText(MainActivity.this, "Waiting for file...", Toast.LENGTH_SHORT).show();
                    new FileServerAsyncTask(MainActivity.this, null).execute();
                }
                else{
                    if(PERMISSIONS_FLAG){
                        Toast.makeText(MainActivity.this, "Waiting for file...", Toast.LENGTH_SHORT).show();
                        serviceIntent = new Intent(MainActivity.this, TransferFile.class);
                        serviceIntent.setAction(TransferFile.ACTION_SEND_FILE);
                        serviceIntent.putExtra(TransferFile.EXTRAS_FILE_PATH, "");
                        serviceIntent.putExtra(TransferFile.EXTRAS_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
                        serviceIntent.putExtra(TransferFile.EXTRAS_GROUP_OWNER_PORT, 8988);
                        serviceIntent.putExtra("EXTENSION", "jpg");
                        serviceIntent.putExtra("SEND_FILE", false);
                        MainActivity.this.startService(serviceIntent);
                        SERVICE_ON = true;
                        System.out.println("Waiting for file, TransferService started.");
                    }
                    else
                        System.out.println("No permission.");
                }
            }
        });

        if(!isConnected) {
            send.setVisibility(View.GONE);
            receive.setVisibility(View.GONE);
        }
        else {
            send.setVisibility(View.VISIBLE);
            receive.setVisibility(View.VISIBLE);
        }
    }

    public String getExtensionType(Uri uri) {
        String extension;
        //Check uri format to avoid null
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            //If scheme is a content
            final MimeTypeMap mime = MimeTypeMap.getSingleton();
            extension = mime.getExtensionFromMimeType(this.getContentResolver().getType(uri));
        } else {
            //If scheme is a File
            //This will replace white spaces with %20 and also other special characters. This will avoid returning null values on file name with spaces and special characters.
            extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(new File(uri.getPath())).toString());
        }
        return extension;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(data!=null) {
            Toast.makeText(MainActivity.this, "Sending file...", Toast.LENGTH_SHORT).show();
            Uri uri = data.getData();
            System.out.println("File extension: "+getExtensionType(uri));
            System.out.println("Bytes: "+ Arrays.toString(getExtensionType(uri).getBytes()));
            if(isHost){
                new FileServerAsyncTask(MainActivity.this, uri).execute();
            }else{
                serviceIntent = new Intent(this, TransferFile.class);
                serviceIntent.setAction(TransferFile.ACTION_SEND_FILE);
                serviceIntent.putExtra(TransferFile.EXTRAS_FILE_PATH, uri.toString());
                serviceIntent.putExtra(TransferFile.EXTRAS_GROUP_OWNER_ADDRESS, info.groupOwnerAddress.getHostAddress());
                serviceIntent.putExtra(TransferFile.EXTRAS_GROUP_OWNER_PORT, 8988);
                serviceIntent.putExtra("EXTENSION", getExtensionType(uri));
                serviceIntent.putExtra("SEND_FILE", true);
                this.startService(serviceIntent);
                SERVICE_ON = true;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // storage-related task you need to do.

                    PERMISSIONS_FLAG = true;

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void connect(int position) {

        final WifiP2pDevice device = peersList.get(position);
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        wifi.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                onConnecting();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Connect failed. Retry.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onConnecting() {
        if (progressDialogConnect != null && progressDialogConnect.isShowing()) {
            progressDialogConnect.dismiss();
        }
        if(!isConnected){
            progressDialogConnect = ProgressDialog.show(MainActivity.this, "Press back to cancel", "Connecting...", false,
                true, new DialogInterface.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (!isConnected)
                            Toast.makeText(MainActivity.this, "Connection failed.", Toast.LENGTH_SHORT).show();
                        wifi.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {

                            }

                            @Override
                            public void onFailure(int reason) {

                            }
                        });
                    }});
            Runnable progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (progressDialogConnect != null && progressDialogConnect.isShowing()) {
                        progressDialogConnect.dismiss();
                        Toast.makeText(MainActivity.this, "Connection request Timed out.", Toast.LENGTH_SHORT).show();
                    }
                }
            };
            Handler pdCanceller = new Handler();
            pdCanceller.postDelayed(progressRunnable, 15000);
        }
    }

    public void disconnect() {
        wifi.removeGroup(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(MainActivity.this, "Device disconnecting failed.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess() {
                send.setVisibility(View.GONE);
                receive.setVisibility(View.GONE);
            }

        });
        refreshList();
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
        if(!isConnected) {
            send.setVisibility(View.GONE);
            receive.setVisibility(View.GONE);
        }
        refreshList();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onDestroy() {
        super.onStop();
        if(SERVICE_ON)
            stopService(serviceIntent);
        if(isConnected)
            disconnect();
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        if (progressDialogDiscover != null && progressDialogDiscover.isShowing()) {
            progressDialogDiscover.dismiss();
        }

        peersList.clear();
        peersList.addAll(peers.getDeviceList());

        refreshList();
    }
    public void refreshList(){
        peersNameList.clear();
        if(peersList.size()!=0){
            for(int i=0;i<peersList.size();i++){
                peersNameList.add(i, peersList.get(i).deviceName);
            }
        }
        else {
            send.setVisibility(View.GONE);
            receive.setVisibility(View.GONE);
        }
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, peersNameList);
        listViewPeer.setAdapter(arrayAdapter);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        this.info = info;
        if (!isConnected) {
            Toast.makeText(MainActivity.this, "Device Connected.", Toast.LENGTH_SHORT).show();
            isConnected = true;

            if (progressDialogConnect != null && progressDialogConnect.isShowing()) {
                progressDialogConnect.dismiss();
            }

            receive.setVisibility(View.VISIBLE);
            send.setVisibility(View.VISIBLE);

            if (info.groupFormed && info.isGroupOwner) {
                isHost = true;
            }
        }
    }

    public void reset(){
        if(isConnected){
            Toast.makeText(MainActivity.this, "Device Disconnected.", Toast.LENGTH_SHORT).show();
            isConnected = false;
            send.setVisibility(View.GONE);
            receive.setVisibility(View.GONE);
            refreshList();
        }
    }
    public class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        Context context;
        Uri uri;
        ServerSocket serverSocket;
        Socket client;

        FileServerAsyncTask(Context context, Uri uri) {
            this.context = context;
            this.uri = uri;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                System.out.println("Waiting for server to connect...");

                serverSocket = new ServerSocket(8988);
                client = serverSocket.accept();

                System.out.println("Server Socket Connected.");

                if(uri == null){
                    if(PERMISSIONS_FLAG) {
                        DataInputStream dIn = new DataInputStream(client.getInputStream());
                        if(dIn.readByte()==1){
                            FILE_EXTENSION = dIn.readUTF();
                            System.out.println("Extension read: "+ FILE_EXTENSION);
                        }

                        final File f = new File(Environment.getExternalStorageDirectory() + "/"
                                + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                                + "." + FILE_EXTENSION);

                        File dirs = new File(f.getParent());
                        if (!dirs.exists())
                            dirs.mkdirs();
                        f.createNewFile();

                        InputStream inputstream = client.getInputStream();
                        copyFile(inputstream, new FileOutputStream(f));
                        System.out.println("File copied to device.");
                        dIn.close();

                        Intent intent = new Intent();
                        intent.setAction(android.content.Intent.ACTION_VIEW);
                        String FILE_TYPE;
                        switch (FILE_EXTENSION) {
                            case "wav":
                            case "mp3":
                                FILE_TYPE = "audio/*";
                                break;
                            case "jpg":
                            case "jpeg":
                            case "png":
                            case "gif":
                                FILE_TYPE = "image/*";
                                break;
                            case "txt":
                                FILE_TYPE = "text/plain";
                                break;
                            case "3gp":
                            case "mpg":
                            case "mpeg":
                            case "mpe":
                            case "mp4":
                            case "avi":
                                FILE_TYPE = "video/*";
                                break;
                            case "pdf":
                                FILE_TYPE = "application/pdf";
                                break;
                            default:
                                FILE_TYPE = "application/*";
                                break;
                        }
                        intent.setDataAndType(Uri.parse("file://" + f.getAbsolutePath()), FILE_TYPE);
                        System.out.println("Inside onPostExecute");
                        context.startActivity(intent);
                    }
                    else
                        System.out.println("No permission.");

                    return null;
                }else{
                    DataOutputStream dOut = new DataOutputStream(client.getOutputStream());
                    dOut.writeByte(1);
                    dOut.writeUTF(getExtensionType(uri));
                    dOut.flush();

                    OutputStream stream = client.getOutputStream();
                    ContentResolver cr = context.getContentResolver();
                    InputStream is = null;
                    try {
                        is = cr.openInputStream(uri);
                    }
                    catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    copyFile(is, stream);
                    dOut.close();
                    return null;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                try {
                    client.close();
                    serverSocket.close();
                    System.out.println("Server socket closed.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        System.out.println("Inside copyFile.");
        long startTime=System.currentTimeMillis();

        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
            long endTime=System.currentTimeMillis()-startTime;
            System.out.println("File copied to client in "+ endTime +" milliseconds");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
}