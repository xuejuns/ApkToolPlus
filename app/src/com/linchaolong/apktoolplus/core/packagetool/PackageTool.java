package com.linchaolong.apktoolplus.core.packagetool;

import com.linchaolong.apktoolplus.core.ApkToolPlus;
import com.linchaolong.apktoolplus.core.KeystoreConfig;
import com.linchaolong.apktoolplus.utils.FileHelper;
import com.linchaolong.apktoolplus.utils.Logger;
import com.linchaolong.apktoolplus.utils.StringUtils;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class PackageTool {

    private final String appIconName;

    public PackageTool(String appIconName) {
        this.appIconName = appIconName;
    }

    private void jar2smali(File jarDir, File smaliDir) {
        FileHelper.delete(smaliDir);
        if (jarDir.exists()) {
            File[] files = jarDir.listFiles();
            if (files != null && files.length > 0) {
                for (File file : files) {
                    if (file.getName().endsWith(".jar")) {
                        Logger.print("jar2smali " + file.getPath() + " to " + smaliDir.getPath());
                        ApkToolPlus.jar2smali(file, smaliDir);
                    }
                }
            }
        }
    }

    private void mergeSDK(SDKConfig sdk, File decompileDir, String packageName, File icon, String label, boolean landscape) {

        File smaliDir = new File(sdk.path, "smali");

        // jar2smali
        jar2smali(new File(sdk.path, "jar"), smaliDir);

        File manifest;
        if (landscape) {
            manifest = new File(sdk.path, "AndroidManifest.xml");
        } else {
            manifest = new File(sdk.path, "AndroidManifest-port.xml");
            if (!manifest.exists()) {
                manifest = new File(sdk.path, "AndroidManifest.xml");
            }
        }

        if (!manifest.exists()) {
            Logger.print(manifest.getPath() + " not exist!!!");
        } else {
            ManifestCombiner manifestCombiner = new ManifestCombiner(
                    new File(decompileDir, "AndroidManifest.xml"),
                    new File[]{manifest},
                    new File(decompileDir, "AndroidManifest.xml"))
                    .setApplicationId(packageName)
                    .setLabel(label);

            if (sdk.metaData != null && !sdk.metaData.isEmpty()) {
                manifestCombiner.setMetadata(sdk.metaData);
            }

            // copy icon
            if (icon != null) {
                FileHelper.copyFile(icon, new File(decompileDir, "res\\mipmap-xxxhdpi-v4\\" + appIconName + ".png"));
                manifestCombiner.setIcon("@mipmap/" + appIconName);
            }

            // 合并manifest
            manifestCombiner.combine();
        }

        // copy smali
        FileHelper.copyDir(smaliDir, new File(decompileDir, "smali"), false);

        // copy assets
        FileHelper.copyDir(new File(sdk.path, "assets"), new File(decompileDir, "assets"), false);

        // copy res
//        FileHelper.copyDir(new File(sdk, "res"), new File(decompileDir, "res"), false);
        ResMerger.copyRes(new File(sdk.path, "res"), new File(decompileDir, "res"));

        // 修改游戏名
        File stringsXml = new File(decompileDir, "res\\values\\strings.xml");
        if (!StringUtils.isEmpty(label) && stringsXml.exists()) {
            Map<String, String> stringMap = new LinkedHashMap<>();
            stringMap.put("app_name", label);
            ResMerger.setString(stringsXml, stringsXml, stringMap);
        }

        // copy appcompat_res
//        ResMerger.copyRes(new File(sdk, "appcompat_res"), new File(decompileDir, "res"));

        // copy so
        ResMerger.copySo(new File(sdk.path, "lib"), new File(decompileDir, "lib"));
    }

    public void copyFile(File decompileDir, Map<File, String> copyFile) {
        if (copyFile != null && !copyFile.isEmpty()) {
            for (Map.Entry<File, String> entry : copyFile.entrySet()) {
                File file = new File(decompileDir, entry.getValue());
                FileHelper.copyFile(entry.getKey(), file);
                Logger.print("copy %s to %s.", entry.getKey().getPath(), file.getPath());
            }
        }
    }

    public void build(BuildConfig buildConfig) {

        String name = FileHelper.getNoSuffixName(buildConfig.apk);
        File dir = buildConfig.apk.getParentFile();

        File decompileDir = new File(dir, name);
        File recompileApk = new File(dir, name + "_recompile.apk");
        File signedApk = new File(dir, name + "_signed.apk");

        // 清理目录
        FileHelper.delete(decompileDir);

        // 解包
        ApkToolPlus.decompile(new File(dir, name + ".apk"), decompileDir, null);

        // 合并sdk
        for (SDKConfig sdk : buildConfig.sdkList) {
            mergeSDK(sdk, decompileDir, buildConfig.packageName, buildConfig.icon, buildConfig.label, buildConfig.landscape);
        }

        // copy file
        copyFile(decompileDir, buildConfig.copyFile);

        // 回编译
        FileHelper.delete(recompileApk);
        ApkToolPlus.recompile(decompileDir, recompileApk, null);

        // 签名
        if (buildConfig.keystoreConfig != null) {
            FileHelper.delete(signedApk);
            ApkToolPlus.signApkV2(buildConfig.apkSigner, recompileApk, signedApk, buildConfig.keystoreConfig);

            FileHelper.delete(recompileApk);
        }
    }

    public static class SDKConfig {
        public File path;
        public Map<String, String> metaData;
        public Map<String, String> placeHolderValues;
    }

    public static class BuildConfig {
        public File apk;
        public SDKConfig[] sdkList;
        public String packageName;
        public File icon;
        public String label;
        public KeystoreConfig keystoreConfig;
        public Map<File, String> copyFile;
        public boolean landscape;
        public File apkSigner;
    }

}