package it.pgp.xfiles.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.pgp.xfiles.MainActivity;
import it.pgp.xfiles.R;
import it.pgp.xfiles.service.BaseBackgroundService;
import it.pgp.xfiles.service.BaseBackgroundTask;
import it.pgp.xfiles.service.ExtractService;
import it.pgp.xfiles.service.HTTPDownloadService;
import it.pgp.xfiles.service.params.DownloadParams;
import it.pgp.xfiles.service.params.ExtractParams;
import it.pgp.xfiles.service.visualization.ViewType;
import it.pgp.xfiles.utils.Misc;
import it.pgp.xfiles.utils.pathcontent.BasePathContent;
import it.pgp.xfiles.utils.pathcontent.LocalPathContent;

public class UpdateCheckDialog extends Dialog {

    private static class JsonParseDuringCompareException extends RuntimeException {
        public JsonParseDuringCompareException(Exception e) {
            super(e);
        }
        public JsonParseDuringCompareException(String msg) {
            super(msg);
        }
    }

    final MainActivity activity;

    final StdDateFormat df = new StdDateFormat();

    String currentVersionTagname,latestVersionTagName,latestVersionDownloadUrl;

    Date currentVersionCreatedAt,latestVersionCreatedAt;

    TextView updateMessage;
    TextView currentVersion, latestVersion;
    Button downloadButton,cancelButton;

    List<Map> releases;

    private void compareReleases(final MainActivity activity) throws ParseException {
        if(releases == null || releases.isEmpty())
            throw new JsonParseDuringCompareException("Null or empty releases list");

        /*Collections.sort(releases,(o1,o2) -> {
            try {
                Date d1 = df.parse((String) o1.get("created_at"));
                Date d2 = df.parse((String) o2.get("created_at"));
                return -(d1.compareTo(d2)); // sort in reversed order
            }
            catch(Exception e) {
                throw new JsonParseDuringCompareException(e);
            }
        });*/

        Map<String,Integer> tagnames = new HashMap<>();
        int cnt=0;
        for(Map<String,String> rel : releases) // actually not Map<String,String>, variable types, but we are interested only in tag_name, so no ClassCastException if the format is the expected one
            tagnames.put(rel.get("tag_name"),cnt++);
        Integer currentVersionIdx = tagnames.get(currentVersionTagname);
        if(currentVersionIdx == null) { // currently installed version not found in GH releases
            // extract date from versionCode (latest 6 chars, yyMMdd)
            currentVersionCreatedAt = new SimpleDateFormat("yyMMdd").parse(
                    currentVersionTagname.substring(currentVersionTagname.length()-6));
        }
        else currentVersionCreatedAt = df.parse((String) releases.get(currentVersionIdx).get("created_at"));

        latestVersionTagName = (String) releases.get(0).get("tag_name");
        latestVersionCreatedAt = df.parse((String) releases.get(0).get("created_at"));
        latestVersionDownloadUrl = (String)((Map)((List)releases.get(0).get("assets")).get(0)).get("browser_download_url");
        activity.runOnUiThread(()->{
            int compareResult = currentVersionCreatedAt.compareTo(latestVersionCreatedAt);
            switch(compareResult) {
                case -1:
                    updateMessage.setText("Update available");
                    break;
                case 0:
                    updateMessage.setText("Already up to date");
                    break;
                case 1:
                    updateMessage.setText("This version is newer than latest official release");
                    break;
                default:
                    throw new RuntimeException("Guard block");
            }
            if(compareResult > -1) {
                downloadButton.setText("Get anyway");
            }

            currentVersion.setText(currentVersionTagname);
            latestVersion.setText(latestVersionTagName);
        });
    }

    public UpdateCheckDialog(@NonNull final MainActivity activity) {
        super(activity);
        this.activity = activity;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.update_check_dialog);
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            currentVersionTagname = pInfo.versionName+"_"+pInfo.versionCode; // STRONG ASSUMPTION
        }
        catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        currentVersion = findViewById(R.id.updateCheckCurrentVersion);
        latestVersion = findViewById(R.id.updateCheckLatestVersion);
        downloadButton = findViewById(R.id.updateCheckOkButton);
        downloadButton.setOnClickListener(this::startDownloadOfLatestRelease);
        cancelButton = findViewById(R.id.updateCheckCancelButton);
        cancelButton.setOnClickListener(v->dismiss());
        updateMessage = findViewById(R.id.updateCheckMessage);
        updateMessage.setText("Checking for updates...");
        new Thread(()->{
            String errMsg;
            Exception ee;
            try {
                byte[] x = MainActivity.getRootHelperClient().downloadHttpsUrlInMemory("api.github.com/repos/pgp/XFiles/releases/latest");
                Log.d(UpdateCheckDialog.class.getName(),new String(x));
                releases = Collections.singletonList(new ObjectMapper().readValue(x, Map.class));
                compareReleases(activity);
                activity.runOnUiThread(()-> downloadButton.setEnabled(true));
                return;
            }
            catch(JsonParseDuringCompareException e) {
                ee = e;
                errMsg = "Json parse error during release sorting";
            }
            catch(JsonParseException | JsonMappingException e) {
                ee = e;
                errMsg = "Json parse error after downloading releases file";
            }
            catch (IOException e) {
                ee = e;
                errMsg = "Prefetch error, check connection";
            }
            catch (ParseException e) {
                ee = e;
                errMsg = "Date parse error";
            }
            catch (Exception e) {
                ee = e;
                errMsg = "Generic error during update check";
            }
            ee.printStackTrace();
            MainActivity.showToast(errMsg);
            dismiss();
        }).start();
    }

    private void startDownloadOfLatestRelease(View unused) {
        /**
         * 1) download latest release zip from GH assets, into internal memory path
         * 2) on complete, extract zip into same folder
         * 3) on extract complete, delete zip and show popup "Install now?"
         */
        Toast.makeText(activity, "Download url: "+latestVersionDownloadUrl, Toast.LENGTH_LONG).show();

        // prepare extract task to be executed after download task has ended
        String[] s = latestVersionDownloadUrl.split("/");
        final String zipname = s[s.length-1];
        final BasePathContent outDir = new LocalPathContent(Misc.internalStorageDir.getAbsolutePath());
        final BasePathContent srcArchive = outDir.concat(zipname);
        String expectedApkName = zipname.substring(0,zipname.length()-3)+"apk";
        final File zipFile = new File(srcArchive.dir);
        final File apkFile = new File(outDir.concat(expectedApkName).dir);

        // add extract task
        BaseBackgroundTask.nextAutoTasks.add(()->{
            if(!zipFile.exists()) {
                BaseBackgroundTask.nextAutoTasks.clear();
                return;
            }
            Intent startIntent = new Intent(activity, ExtractService.class);
            startIntent.setAction(BaseBackgroundService.START_ACTION);
            startIntent.putExtra(
                    "params",
                    new ExtractParams(
                            Collections.singletonList(srcArchive),
                            outDir,
                            null,
                            null,
                            false
                    ));
            activity.startService(startIntent);
        });

        // add install task (delete zipped apk file as well
        BaseBackgroundTask.nextAutoTasks.add(()->{
            if(!apkFile.exists()) {
                BaseBackgroundTask.nextAutoTasks.clear();
                return;
            }
            try {
                MainActivity.getRootHelperClient().deleteFilesOrDirectories(Collections.singletonList(srcArchive));
            }
            catch (IOException|NullPointerException e) {
                MainActivity.showToast("Unable to delete zipped apk file");
            }

            // show dialog asking if installation should be done now
            activity.runOnUiThread(()->{
                AlertDialog.Builder bld = new AlertDialog.Builder(activity);
                bld.setTitle("APK file has been downloaded, install now?");
                bld.setNegativeButton(android.R.string.cancel, null);
                bld.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // kill RH before updating; this is useful in older versions of Android with root,
                    // since the RH process (being root) cannot be terminated till restart
                    // (even if the executable on the filesystem is actually updated)
                    MainActivity.killRHWrapper();
                    activity.installApk(apkFile);
                });
                AlertDialog alertDialog = bld.create();
                alertDialog.getWindow().setType(ViewType.OVERLAY_WINDOW_TYPE);
                alertDialog.show();
            });
        });

        // run download task, subsequent tasks will be run automatically after download end
        Intent relDownloadIntent = new Intent(activity, HTTPDownloadService.class);
        relDownloadIntent.setAction(BaseBackgroundService.START_ACTION);
        relDownloadIntent.putExtra("params",new DownloadParams(
                latestVersionDownloadUrl,
                Misc.internalStorageDir.getAbsolutePath(),
                zipname,
                true));
        activity.startService(relDownloadIntent);
        dismiss();
    }
}
