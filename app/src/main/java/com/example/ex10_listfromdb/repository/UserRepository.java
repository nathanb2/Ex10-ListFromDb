package com.example.ex10_listfromdb.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;

import com.example.ex10_listfromdb.database.SharedPredferences;
import com.example.ex10_listfromdb.database.dao.UserDao;
import com.example.ex10_listfromdb.model.DataResponse;
import com.example.ex10_listfromdb.model.User;
import com.example.ex10_listfromdb.networking.RetrofitHelper;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static com.example.ex10_listfromdb.model.DataResponse.RequestStatus;

public class UserRepository {

    private final Executor executor;
    UserDao userDao;

    public UserRepository(UserDao userDao, Executor executor) {
        this.userDao = userDao;
        this.executor = executor;
    }

    public void createUser(User user) {
        userDao.createUser(user);
    }


    public void updateUser(User user) {
        userDao.updateUser(user);
    }

    public void removeUser(User user) {
        userDao.removeUser(user);
    }

    public LiveData<User> getUserById(long id) {
        return userDao.getUserById(id);
    }

    public MediatorLiveData<DataResponse<List<User>>> getAllUsers() {
        DataResponse<List<User>> dataResponse = new DataResponse<>(new ArrayList<>());
        MediatorLiveData<DataResponse<List<User>>> usersMediatorLiveData = new MediatorLiveData();
        usersMediatorLiveData.addSource(userDao.getAllUsers(), new Observer<List<User>>() {
            @Override
            public void onChanged(List<User> users) {
                if (users != null && users.size() > 0) {
                    dataResponse.setBody(users);
                    dataResponse.setRequestStatus(RequestStatus.SUCCESS_LOCAL);
                    usersMediatorLiveData.postValue(dataResponse);
                }
            }
        });
        fetchUsers(dataResponse, usersMediatorLiveData);
        return usersMediatorLiveData;
    }

    public void fetchUsers(DataResponse<List<User>> dataResponse, MediatorLiveData usersMediatorLiveData) {
        SharedPredferences sharedPredferences = SharedPredferences.getInstance();
        if (sharedPredferences.getLastGithubUsersFetch() < new Date().getTime() - MINUTE_IN_MILLIS) {
            dataResponse.setRequestStatus(RequestStatus.LOADING);
            usersMediatorLiveData.postValue(dataResponse);
            Call<List<User>> call = RetrofitHelper.getGithubApiService().getGithubUsers();

            call.enqueue(new Callback<List<User>>() {
                @Override
                public void onResponse(@NotNull Call<List<User>> call, @NotNull Response<List<User>> response) {

                    createResponseObject(response, dataResponse, List.class);
                    usersMediatorLiveData.postValue(dataResponse);

                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            userDao.createUsers(response.body());
                        }
                    });
                    sharedPredferences.setLastGithubUsersFetch(new Date().getTime());
                }

                @Override
                public void onFailure(@NotNull Call<List<User>> call, @NotNull Throwable t) {
                    dataResponse.setRequestStatus(RequestStatus.ERROR);
                    usersMediatorLiveData.postValue(dataResponse);
                }
            });
        }
    }

    private void createResponseObject(@NotNull Response<List<User>> response, DataResponse<List<User>> dataResponse, Class<List> responseType) {
        if (response.body() != null) {
            dataResponse.setBody(response.body());
            if ((responseType.isAssignableFrom(List.class) || responseType.isAssignableFrom(ArrayList.class)
                    && (new ArrayList<>(response.body()).size() > 0))) {
                dataResponse.setRequestStatus(RequestStatus.SUCCESS_SERVER);
            } else {
                dataResponse.setRequestStatus(RequestStatus.NO_DATA);
            }
        }else {
            dataResponse.setRequestStatus(RequestStatus.ERROR);
        }
    }
}
