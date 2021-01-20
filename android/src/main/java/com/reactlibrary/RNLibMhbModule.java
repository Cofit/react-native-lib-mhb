package com.reactlibrary;

import android.content.SharedPreferences;
import android.util.Base64;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.WritableMap;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

import com.nhi.mhbsdk.MHB;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class RNLibMhbModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    public MHB mhb;

    public RNLibMhbModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNLibMhb";
    }

    @ReactMethod
    public void initialize() {
        try {
            this.mhb = MHB.configure(getReactApplicationContext().getCurrentActivity(), BuildConfig.API_KEY);
        } catch(Exception e) {
            System.err.println(e);
        }
    }

    public File fisToFile(FileInputStream fis, String filename) throws IOException {
        File directory = this.reactContext.getFilesDir();
        File outputFile = new File(directory, filename);
        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            int length;
            byte[] bytes = new byte[1024];
            while ((length = fis.read(bytes)) != -1) {
                fos.write(bytes, 0, length);
            }
            fis.close();
            fos.close();
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        }
        return outputFile;
    }

    public String fileToString(File file) throws IOException {
        int length = (int) file.length();

        byte[] bytes = new byte[length];
        FileInputStream in;
        try {
            in = new FileInputStream(file);
            in.read(bytes);
            in.close();
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        }
        return new String(bytes);
    }

    public List<String> getFNListFromZipFP(String path) throws ZipException{
        List<String> list = new ArrayList<String>();
        try {
            List<FileHeader> fileHeaders = new ZipFile(path).getFileHeaders();
            for (FileHeader fh : fileHeaders) {
                list.add(fh.getFileName());
            }
        } catch (ZipException e) {
            throw e;
        }
        return list;
    }

    public String getDecryptKey(String password, String salt) {
        byte[] bytes = salt.getBytes(StandardCharsets.US_ASCII);

        SecretKeyFactory factory = null;
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e2) {
            e2.printStackTrace();
        }

        KeySpec spec = new PBEKeySpec(password.toCharArray(), bytes, 1000, 256);
        SecretKey tmp = null;
        try {
            tmp = factory.generateSecret(spec);
        } catch (InvalidKeySpecException e2) {
            e2.printStackTrace();
        }
        String finalkey = Base64.encodeToString(tmp.getEncoded(), Base64.DEFAULT).trim();

        return finalkey;
    }

    @ReactMethod
    public void startProc(final Promise promise) {
        //先清File Tickets, 避免errorCode 101
        SharedPreferences sharedPreferences= this.reactContext.getSharedPreferences(this.reactContext.getPackageName(), android.app.Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Map<String, ?> allKey = sharedPreferences.getAll();
        for (Map.Entry<String, ?> entry : allKey.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("File_Ticket_")) {
                editor.remove(key);
                editor.apply();
            }
        }

        this.mhb.startProc(new MHB.StartProcCallback() {
            @Override
            public void onStarProcSuccess() {
                //畫面已render
                System.out.print("onUIProcStart...");
                promise.resolve("OK");
            }

            @Override
            public void onStartProcFailure(String errorCode) {
                promise.reject(errorCode, new Error(errorCode));
            }
        });
    }

    @ReactMethod
    public void fetchData(
            final String startTimestamp,
            final String endTimestamp,
            final Promise promise
    ) {
        //初始化 SharedPreferences
        SharedPreferences sharedPreferences= this.reactContext.getSharedPreferences(this.reactContext.getPackageName(), android.app.Activity.MODE_PRIVATE);
        //以下列出檔案識別碼遞回查尋範例供參考使用
        Map<String, ?> allKey = sharedPreferences.getAll();
        //先iterate一遍檢查有沒有符合的File Ticket
        boolean hasValidFileTicket = false;
        for (Map.Entry<String, ?> entry : allKey.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("File_Ticket_")) {
                String timeStamp = key.split("ket_")[1];
                if (
                        startTimestamp.compareTo(timeStamp) < 0 && endTimestamp.compareTo(timeStamp) > 0
                ) {
                    hasValidFileTicket = true;
                }
            }
        }
        if (!hasValidFileTicket) {
            promise.reject(BuildConfig.ENOVALFT, new Error(BuildConfig.ENOVALFT));
        }
        for (Map.Entry<String, ?> entry : allKey.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("File_Ticket_")) {
                //可依已紀錄的起始/結束時間戳記區間內查詢前次 SDK 存入的檔案識別碼
                String timeStamp = key.split("ket_")[1];
                if (
                        startTimestamp.compareTo(timeStamp) < 0 && endTimestamp.compareTo(timeStamp) > 0
                ) {
                    //此key為要送給fetchData的File Ticket Name.
                    //實作取檔 callback
                    final WritableMap map = new WritableNativeMap();
                    this.mhb.fetchData(key, new MHB.FetchDataCallback(){
                        @Override
                        public void onFetchDataSuccess(FileInputStream fis,String serverKey){
                            try {
                                File tmp = fisToFile(fis, "tmp.zip");
                                String path = tmp.getAbsolutePath();
                                List<String> filenameList = getFNListFromZipFP(path);

                                //解密時，需以 Api_Key 及 Server_Key 的 PBKDF2 運算結果做為金鑰，之後以此金鑰來解密該檔案，並將結果儲存為.json 檔
                                String decryptKey = getDecryptKey(BuildConfig.API_KEY, serverKey);
                                new ZipFile(path, decryptKey.toCharArray()).extractAll(reactContext.getFilesDir().getAbsolutePath());
                                //將 zip 檔解出來的所有.json 檔案進行拼接
                                for(int i = 0; i < filenameList.size(); i++) {
                                    File f = new File(reactContext.getFilesDir().getAbsolutePath(), filenameList.get(i));
                                    String res = fileToString(f);
                                    map.putString(filenameList.get(i), res);
                                }
                            } catch (Exception e) {
                                promise.reject(e.getMessage(), e.getMessage());
                            }

                            promise.resolve(map);
                        }

                        @Override
                        public void onFetchDataFailure(String errorCode){
                            promise.reject(errorCode, new Error(errorCode));
                        }
                    });
                }
            }
        }
    }
}
