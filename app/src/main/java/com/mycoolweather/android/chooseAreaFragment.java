package com.mycoolweather.android;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mycoolweather.android.db.City;
import com.mycoolweather.android.db.County;
import com.mycoolweather.android.db.Province;
import com.mycoolweather.android.util.HttpUtil;
import com.mycoolweather.android.util.Utility;

import org.litepal.LitePal;
import org.litepal.crud.LitePalSupport;
import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;


public class chooseAreaFragment extends Fragment {
    public static final int LEVE_PROVINCE = 0;
    public static final int LEVE_CITY = 1;
    public static final int LEVE_COUNTY = 2;
    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>();

    // 省列表
    private List<Province> provinceList;
    // 市列表
    private List<City> cityList;
    // 县列表
    private List<County> countyList;
    // 选中的省份
    private Province selectedProvince;
    // 选中的城市
    private City selectedCity;
    // 当前选中的级别
    private int currentLevel;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container,false);
        titleText = (TextView) view.findViewById(R.id.title_text);
        backButton = (Button) view.findViewById(R.id.back_button);
        listView = (ListView) view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        return view;
    }

    public void onActivityCreated(Bundle saveInstanceState) {

        super.onActivityCreated(saveInstanceState);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (currentLevel == LEVE_PROVINCE) {
                    selectedProvince = provinceList.get(i);
                    queryCityes();
                } else if (currentLevel == LEVE_CITY) {
                    selectedCity = cityList.get(i);
                    queryCounties();
                } else if (currentLevel == LEVE_COUNTY) {
                    String weatherId = countyList.get(i).getWeatherId();
                    Intent intent = new Intent(getActivity(), WeatherActivity.class);
                    intent.putExtra("weather_id", weatherId);
                    startActivity(intent);
                    getActivity().finish();
                }
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentLevel == LEVE_COUNTY) {
                    queryCityes();
                } else if (currentLevel == LEVE_CITY) {
                    queryProvinces();
                }
            }
        });

        queryProvinces();
    }
    // 查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器请求
    private void queryProvinces() {
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        provinceList = LitePal.findAll(Province.class);

        if (provinceList.size() > 0) {
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVE_PROVINCE;
        } else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,"province");
        }
    }

    // 查询选中省内所有的市，优先从数据库查询，如果没有查询到再去服务器查询
    private void queryCityes() {
        // 显示选中的省
        titleText.setText(selectedProvince.getProvinceName());

        cityList = LitePal.where("provinceId = ?", String.valueOf(selectedProvince.getId())).find(City.class);
        if (cityList.size() > 0) {
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());
            }

            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVE_CITY;
            backButton.setVisibility(View.VISIBLE);
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address,"city");
        }
    }

    // 查询选中市内所有的县，优先从数据库查询，如果没有查询到再去服务器上查询
    private void queryCounties () {
        titleText.setText(selectedCity.getCityName());

        countyList = LitePal.where("cityId = ?", String.valueOf(selectedCity.getId())).find(County.class);
        if (countyList.size() > 0) {
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVE_COUNTY;
            backButton.setVisibility(View.VISIBLE);
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(address, "county");
        }
    }

    // 根据传入的地址和类型从服务器上查询省市县数据

    private void queryFromServer(String address, final String type) {
        Log.d("CoolLog","请求链接："+address);
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // 通过runOnUiThread()方法回到主线程处理逻辑
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(),"加载失败", Toast.LENGTH_SHORT).show();

                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseText = response.body().string();
                    boolean result = false;
                    if ("province".equals(type)) {
                        result = Utility.handleProinceResponse(responseText);
                    } else if ("city".equals(type)) {
                        result = Utility.handleCityResponse(responseText, selectedProvince.getId());
                    } else if ("county".equals(type)) {
                        result = Utility.handleCountyResponse(responseText,selectedCity.getId());
                    }
                    if (result) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                closeProgressDialog();
                                if ("province".equals(type)) {
                                    queryProvinces();
                                } else if ("city".equals(type)) {
                                    queryCityes();
                                } else if ("county".equals(type)) {
                                    queryCounties();
                                }
                                backButton.setVisibility(View.VISIBLE);
                            }
                        });
                    }
            }
        });
    }

    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog((getActivity()));
            progressDialog.setMessage("正在加载中.....");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    private void closeProgressDialog () {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }
}