package com.apposite.wifip2p;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TransferFile extends IntentService{

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "com.apposite.wifip2p.SEND_FILE";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";

    public TransferFile(){
        super("TransferFile");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            boolean action = intent.getExtras().getBoolean("SEND_FILE");
            String fileUri = intent.getExtras().getString(EXTRAS_FILE_PATH);
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            String FILE_EXTENSION = intent.getExtras().getString("EXTENSION");
            Socket socket = new Socket();
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

            try {
                System.out.println("Waiting for client to connect... ");

                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                System.out.println("Client Socket Connected.");
                if(action){
                    DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
                    dOut.writeByte(1);
                    dOut.writeUTF(FILE_EXTENSION);
                    dOut.flush();

                    OutputStream stream = socket.getOutputStream();
                    ContentResolver cr = context.getContentResolver();
                    InputStream is = null;
                    try {
                        is = cr.openInputStream(Uri.parse(fileUri));
                    }
                    catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    MainActivity.copyFile(is, stream);
                    dOut.close();
                }else{
                    DataInputStream dIn = new DataInputStream(socket.getInputStream());
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

                    InputStream inputstream = socket.getInputStream();
                    MainActivity.copyFile(inputstream, new FileOutputStream(f));
                    dIn.close();

                    Intent openFile = new Intent();
                    openFile.setAction(android.content.Intent.ACTION_VIEW);
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
                    openFile.setDataAndType(Uri.parse("file://" + f.getAbsolutePath()), FILE_TYPE);
                    openFile.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(openFile);
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (socket.isConnected()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("socket closed.");
            }
        }
    }
}