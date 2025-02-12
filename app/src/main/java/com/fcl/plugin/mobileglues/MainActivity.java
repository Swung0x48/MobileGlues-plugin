package com.fcl.plugin.mobileglues;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import static java.sql.Types.NULL;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fcl.plugin.mobileglues.settings.MGConfig;
import com.fcl.plugin.mobileglues.utils.ResultListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainActivity extends ComponentActivity implements AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener {

    private MGConfig config = null;

    private Button openOptions;

    private LinearLayout optionLayout;
    private Spinner angleSpinner;
    private Spinner noErrorSpinner;
    private Switch extGL43Switch;
    private Switch extCsSwitch;
    private EditText inputMaxGlslCacheSize;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        openOptions = findViewById(R.id.open_options);

        inputMaxGlslCacheSize = findViewById(R.id.input_max_glsl_cache_size);
        optionLayout = findViewById(R.id.option_layout);
        angleSpinner = findViewById(R.id.spinner_angle);
        noErrorSpinner = findViewById(R.id.spinner_no_error);
        extGL43Switch = findViewById(R.id.switch_ext_gl43);
        extCsSwitch = findViewById(R.id.switch_ext_cs);

        ArrayList<String> angleOptions = new ArrayList<>();
        angleOptions.add(getString(R.string.option_angle_disable_if_possible));
        angleOptions.add(getString(R.string.option_angle_enable_if_possible));
        angleOptions.add(getString(R.string.option_angle_disable));
        angleOptions.add(getString(R.string.option_angle_enable));
        ArrayAdapter<String> angleAdapter = new ArrayAdapter<>(this, R.layout.spinner, angleOptions);
        angleSpinner.setAdapter(angleAdapter);

        ArrayList<String> noErrorOptions = new ArrayList<>();
        noErrorOptions.add(getString(R.string.option_no_error_auto));
        noErrorOptions.add(getString(R.string.option_no_error_enable));
        noErrorOptions.add(getString(R.string.option_no_error_disable_pri));
        noErrorOptions.add(getString(R.string.option_no_error_disable_sec));
        ArrayAdapter<String> noErrorAdapter = new ArrayAdapter<>(this, R.layout.spinner, noErrorOptions);
        noErrorSpinner.setAdapter(noErrorAdapter);

        openOptions.setOnClickListener(view -> checkPermission());

        checkPermissionSilently();
    }

    private void showOptions() {
        try {
            config = MGConfig.loadConfig();

            if (config == null)
                config = new MGConfig(0, 0, 0, 0, 0);

            if (config.getEnableANGLE() > 3 || config.getEnableANGLE() < 0)
                config.setEnableANGLE(0);
            if (config.getEnableNoError() > 3 || config.getEnableNoError() < 0)
                config.setEnableNoError(0);
            
            if (config.getMaxGlslCacheSize() == NULL)
                config.setMaxGlslCacheSize(30);
            
            inputMaxGlslCacheSize.setText(String.valueOf(config.getMaxGlslCacheSize()));
            angleSpinner.setSelection(config.getEnableANGLE());
            noErrorSpinner.setSelection(config.getEnableNoError());
            extGL43Switch.setChecked(config.getEnableExtGL43() == 1);
            extCsSwitch.setChecked(config.getEnableExtComputeShader() == 1);

            angleSpinner.setOnItemSelectedListener(this);
            noErrorSpinner.setOnItemSelectedListener(this);
            extGL43Switch.setOnCheckedChangeListener(this);
            extCsSwitch.setOnCheckedChangeListener(this);
            inputMaxGlslCacheSize.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    String text = s.toString();
                    if (!text.isEmpty()) {
                        try {
                            int number = Integer.parseInt(text);
                            if (number < 0) {
                                inputMaxGlslCacheSize.setError("Error: number cannot be less than 0.");
                            }
                            config.setMaxGlslCacheSize(number);
                        } catch (NumberFormatException e) {
                            inputMaxGlslCacheSize.setError("Error: invalid number.");
                        } catch (IOException e) {
                            inputMaxGlslCacheSize.setError("Error: unexpected error.");
                            throw new RuntimeException(e);
                        }
                    }
                }

                @Override
                public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence charSequence, int start, int before, int after) {}
            });

            openOptions.setVisibility(View.GONE);
            optionLayout.setVisibility(View.VISIBLE);
        } catch (IOException e) {
            Logger.getLogger("MG").log(Level.SEVERE, "Failed to load config! Exception: ", e.getCause());
            Toast.makeText(this, getString(R.string.warning_load_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkPermissionSilently() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                showOptions();
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                showOptions();
            }
        }
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                showOptions();
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                ResultListener.startActivityForResult(this, intent, 1000, (requestCode, resultCode, data) -> {
                    if (requestCode == 1000) {
                        if (Environment.isExternalStorageManager()) {
                            showOptions();
                        } else {
                            recheckPermission();
                        }
                    }
                });
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                showOptions();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE}, 1000);
            }
        }
    }

    private void recheckPermission() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle(getString(R.string.dialog_permission_title));
        builder.setMessage(getString(R.string.dialog_permission_msg));
        builder.setPositiveButton(R.string.dialog_positive, (dialogInterface, i) -> checkPermission());
        builder.setNegativeButton(R.string.dialog_negative, (dialogInterface, i) -> {
            // Do nothing here.
        });
        builder.create().show();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (adapterView == angleSpinner && config != null) {
            try {
                if (i == 3 && isAdreno740()) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.dialog_title_warning))
                            .setMessage(getString(R.string.warning_adreno_740_angle))
                            .setPositiveButton(getString(R.string.dialog_positive), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        config.setEnableANGLE(i);
                                    } catch (IOException e) {
                                        Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                                        Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .setNegativeButton(getString(R.string.dialog_negative), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    angleSpinner.setSelection(config.getEnableANGLE());
                                }
                            })
                            .show();
                } else {
                    config.setEnableANGLE(i);
                }
            } catch (IOException e) {
                Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e.getCause());
                Toast.makeText(this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
            }
        }
        
        if (adapterView == noErrorSpinner && config != null) {
            try {
                config.setEnableNoError(i);
            } catch (IOException e) {
                Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e.getCause());
                Toast.makeText(this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
        if (compoundButton == extGL43Switch && config != null) {
            if (isChecked) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.dialog_title_warning))
                        .setMessage(getString(R.string.warning_ext_gl43_enable))
                        .setCancelable(false)
                        .setOnKeyListener(new DialogInterface.OnKeyListener() {
                            @Override
                            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                                return keyCode == KeyEvent.KEYCODE_BACK;
                            }
                        })
                        .setPositiveButton(getString(R.string.dialog_positive), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    config.setEnableExtGL43(1);
                                } catch (IOException e) {
                                    Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                                    Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton(getString(R.string.dialog_negative), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                extGL43Switch.setChecked(false);
                            }
                        })
                        .show();
            } else {
                try {
                    config.setEnableExtGL43(0);
                } catch (IOException e) {
                    Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                    Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                }
            }
        }
        if (compoundButton == extCsSwitch && config != null) {
            if (isChecked) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.dialog_title_warning))
                        .setMessage(getString(R.string.warning_ext_cs_enable)).setCancelable(false)
                        .setOnKeyListener(new DialogInterface.OnKeyListener() {
                            @Override
                            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                                return keyCode == KeyEvent.KEYCODE_BACK;
                            }
                        })
                        .setPositiveButton(getString(R.string.dialog_positive), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    config.setEnableExtComputeShader(1);
                                } catch (IOException e) {
                                    Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                                    Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton(getString(R.string.dialog_negative), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                extCsSwitch.setChecked(false);
                            }
                        })
                        .show();
            } else {
                try {
                    config.setEnableExtComputeShader(0);
                } catch (IOException e) {
                    Logger.getLogger("MG").log(Level.SEVERE, "Failed to save config! Exception: ", e);
                    Toast.makeText(MainActivity.this, getString(R.string.warning_save_failed), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ResultListener.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1000) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showOptions();
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) || !ActivityCompat.shouldShowRequestPermissionRationale(this, READ_EXTERNAL_STORAGE)) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    ResultListener.startActivityForResult(this, intent, 1000, (requestCode1, resultCode, data) -> {
                        if (requestCode1 == 1000) {
                            if (ActivityCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                showOptions();
                            } else {
                                onRequestPermissionsResult(requestCode1, permissions, grantResults);
                            }
                        }
                    });
                } else {
                    checkPermission();
                }
            }
        }
    }

    private String getGPUName() {
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            return null;
        }

        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            return null;
        }

        int[] configAttributes = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        android.opengl.EGLConfig[] eglConfigs = new android.opengl.EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, eglConfigs, 0, eglConfigs.length, numConfigs, 0)) {
            EGL14.eglTerminate(eglDisplay);
            return null;
        }

        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext eglContext = EGL14.eglCreateContext(eglDisplay, eglConfigs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglTerminate(eglDisplay);
            return null;
        }

        int[] surfaceAttributes = {
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
        };
        EGLSurface eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfigs[0], surfaceAttributes, 0);
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglTerminate(eglDisplay);
            return null;
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglTerminate(eglDisplay);
            return null;
        }

        String renderer = GLES20.glGetString(GLES20.GL_RENDERER);

        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(eglDisplay, eglSurface);
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        EGL14.eglTerminate(eglDisplay);

        return renderer;
    }

    private boolean isAdreno740() {
        String renderer = getGPUName();
        return renderer != null && renderer.toLowerCase().contains("adreno") && renderer.contains("740");
    }

}
