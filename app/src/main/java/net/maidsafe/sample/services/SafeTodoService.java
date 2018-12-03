package net.maidsafe.sample.services;

import android.content.Context;
import android.net.Uri;

import net.maidsafe.api.Client;
import net.maidsafe.api.model.App;
import net.maidsafe.api.model.Request;
import net.maidsafe.safe_app.AppExchangeInfo;
import net.maidsafe.safe_app.AuthReq;
import net.maidsafe.safe_app.ContainerPermissions;
import net.maidsafe.safe_app.MDataEntry;
import net.maidsafe.safe_app.MDataInfo;
import net.maidsafe.sample.model.Task;
import net.maidsafe.sample.model.TodoList;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SafeTodoService implements ITodoService, net.maidsafe.api.listener.OnDisconnected {

    private final SafeApi api;
    private MDataInfo appData;
    final App app = new App("net.maidsafe.sample", "Safe TODO Java",
                          "Maidsafe.net", "0.1.0");
    private static final String AUTH_URL_PREFIX = "safe-auth://";
    private static final int TYPE_TAG = 16290;
    private static final int INT_SIZE = 4;
    private OnDisconnected onDisconnected;

    @Override
    public void disconnected(final Object o) {
        if (onDisconnected == null) {
            return;
        }
        onDisconnected.onDisconnected();
    }

    public SafeTodoService(final Context context) {
        api = SafeApi.getInstance(context);
    }

    @Override
    public String generateAuthURL() throws Exception {
        final AppExchangeInfo appExchangeInfo = new AppExchangeInfo(app.getId(), "",
                app.getName(), app.getVendor());
        final ContainerPermissions[] permissions = new ContainerPermissions[0];
        final AuthReq authReq = new AuthReq(appExchangeInfo, true,
                permissions, permissions.length, 0);
        final Request request = Client.encodeAuthReq(authReq).get();

        return AUTH_URL_PREFIX + app.getId() + '/' + request.getUri();

    }

    @Override
    public void connect(final Uri authResponse, final OnDisconnected disconnected) throws Exception {
        final String response = authResponse.toString().replaceAll(".*\\/+", "");
        onDisconnected = disconnected;
        api.connect(response, app.getId(), this);
    }

    @Override
    public void getAppData() throws Exception {
        appData =  api.getSectionsFromAppContainer();
    }

    @Override
    public long getSectionsLength() throws Exception {
        return api.getEntriesLength(appData);
    }

    @Override
    public List<TodoList> fetchSections() throws Exception {
        final List<TodoList> sectionsList = new ArrayList<>();
        final List<MDataEntry> entries = api.getEntries(appData);
        entries.forEach(mDataEntry -> {
            if (mDataEntry.getValue().getContentLen() != 0) {
                sectionsList.add(TodoList.getListInfo(mDataEntry.getValue().getContent()));
            }
        });
        return sectionsList;
    }

    @Override
    public TodoList addSection(final String sectionTitle) throws Exception {
        final MDataInfo listInfo = api.newMutableData(TYPE_TAG);
        api.insertPermissions(listInfo);
        final byte[] serializedMdInfo = api.serializeMdInfo(listInfo);
        final TodoList list = new TodoList(sectionTitle, new Date(), serializedMdInfo);
        if (api.getEntriesLength(appData) == 0) {
            api.insertPermissions(appData);
        }
        final byte[] key = getByteArray(list.getDate().hashCode());
        final byte[] value = list.toStream();
        api.addEntry(key, value, appData);
        return list;
    }

    @Override
    public List<Task> fetchListItems(final TodoList listInfo) throws Exception {
        final MDataInfo mDataInfo = api.deserializeMdInfo(listInfo.getContent());
        final List<Task> taskList = new ArrayList<>();
        final List<MDataEntry> entries = api.getEntries(mDataInfo);
        entries.forEach(mDataEntry -> {
            if (mDataEntry.getValue().getContentLen() != 0) {
                taskList.add(Task.toTask(mDataEntry.getValue().getContent()));
            }
        });
        return taskList;
    }

    @Override
    public void addTask(final Task task, final TodoList listInfo) throws Exception {
        final byte[] key = getByteArray(task.getDate().hashCode());
        final byte[] value = task.toStream();
        final MDataInfo mDataInfo = api.deserializeMdInfo(listInfo.getContent());
        api.addEntry(key, value, mDataInfo);
    }

    @Override
    public void deleteTask(final Task task, final TodoList listInfo) throws Exception {
        final MDataInfo mDataInfo = api.deserializeMdInfo(listInfo.getContent());
        task.setVersion(task.getVersion() + 1);
        final byte[] key = getByteArray(task.getDate().hashCode());
        api.deleteEntry(key, task.getVersion(), mDataInfo);
    }

    @Override
    public void updateTaskStatus(final Task task, final TodoList listInfo) throws Exception {
        final MDataInfo mDataInfo = api.deserializeMdInfo(listInfo.getContent());
        task.setComplete(!task.getComplete());
        task.setVersion(task.getVersion() + 1);
        final byte[] key = getByteArray(task.getDate().hashCode());
        final byte[] newValue = task.toStream();
        api.updateEntry(key, newValue, task.getVersion(), mDataInfo);
    }

    @Override
    public long getEntriesLength(final TodoList listInfo) throws Exception {
        final MDataInfo mDataInfo = api.deserializeMdInfo(listInfo.getContent());
        return api.getEntriesLength(mDataInfo);
    }

    @Override
    public void reconnect() throws Exception {
        api.reconnect();
    }

    private byte[] getByteArray(final int x) {
        return ByteBuffer.allocate(INT_SIZE).putInt(x).array();
    }

}
