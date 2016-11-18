package com.cloudbees.lxd.client;

import com.cloudbees.lxd.client.api.AsyncOperation;
import com.cloudbees.lxd.client.api.ContainerAction;
import com.cloudbees.lxd.client.api.ContainerInfo;
import com.cloudbees.lxd.client.api.ContainerState;
import com.cloudbees.lxd.client.api.Device;
import com.cloudbees.lxd.client.api.ImageAliasesEntry;
import com.cloudbees.lxd.client.api.ImageInfo;
import com.cloudbees.lxd.client.api.LxdResponse;
import com.cloudbees.lxd.client.api.ServerState;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import static java.lang.String.format;

public class DefaultLXDClient implements AutoCloseable {

    private static final String RECURSION_SUFFIX = "?recursion=1";

    protected final RequestContext ctx;

    public DefaultLXDClient() {
        this(Config.localAccessConfig());
    }

    public DefaultLXDClient(Config config) {
        this.ctx = new RequestContext(config);
    }

    @Override
    public void close() throws Exception {
        ctx.close();
    }

    public ServerState serverStatus() {
        return ctx.get(null).build().execute().parseSync(new TypeReference<LxdResponse<ServerState>>() {});
    }

    public List<ContainerInfo> listContainers() {
        return ctx.get("containers" + RECURSION_SUFFIX).build().execute().parseSync(new TypeReference<LxdResponse<List<ContainerInfo>>>() {});
    }

    public ContainerInfo containerInfo(String name) {
        return ctx.get(format("containers/%s", name)).build().expect(200, 404).execute().parseSync(new TypeReference<LxdResponse<ContainerInfo>>() {});
    }

    public ContainerState containerState(String name) {
        return ctx.get(format("containers/%s/state", name)).build().expect(200, 404).execute().parseSync(new TypeReference<LxdResponse<ContainerState>>() {});
    }

    /**
     *
     * @param name
     * @param imgremote either null for the local LXD daemon or one of remote name defined in {@link Config#remotesURL}
     * @param image
     * @param profiles
     * @param config
     * @param devices
     * @param ephem
     * @return
     */
    public AsyncOperation containerInit(String name, String imgremote, String image, List<String> profiles, Map<String, String> config, List<Device> devices, boolean ephem) {
        ServerState serverState = serverStatus();
        serverState.getEnvironment().getArchitectures();

        Map<String, String> source = new HashMap<>();
        source.put("type", "image");
        if (imgremote != null) {
            source.put("server", ctx.getConfig().getRemotesURL().get(imgremote));
            source.put("protocol", "simplestreams");
            // source.put("certificate", ); <= fetch the cert?
            source.put("fingerprint", image);
        } else {
            ImageAliasesEntry alias = imageGetAlias(image);
            String fingerprint = alias != null ? alias.getTarget() : image;
            ImageInfo imageInfo = imageInfo(fingerprint);
            if (imageInfo == null) {
                throw new LxdClientException("Unable to find image locally");
            }
            source.put("fingerprint", fingerprint);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("source", source);

        if (name != null && !name.isEmpty()) {
            body.put("name", name);
        }
        if (profiles != null && !profiles.isEmpty()) {
            body.put("profiles", profiles);
        }
        if (config != null && !config.isEmpty()) {
            body.put("config", config);
        }
        if (devices != null && !devices.isEmpty()) {
            body.put("devices", devices);
        }
        if (ephem) {
            body.put("ephem", ephem);
        }

        return ctx.post("containers").body(body).build().expect(202).execute().parseAsyncOperation();
    }

    public AsyncOperation containerAction(String name, ContainerAction action, int timeout, boolean force, boolean stateful) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", action.getValue());
        body.put("timeout", timeout);
        body.put("force", force);

        switch (action) {
            case Start:
            case Stop:
                body.put("stateful", stateful);
        }

        return ctx.put(format("containers/%s/state", name)).body(body).build().expect(202).execute().parseAsyncOperation();
    }

    public AsyncOperation containerDelete(String name, ContainerAction action, int timeout, boolean force, boolean stateful) {
        String[] slashSplitted = name.split("/", 1);
        String url = slashSplitted.length == 1 ? format("containers/%s", name) : format("containers/%s/snapshots/%s", slashSplitted[0], slashSplitted[1]);
        return ctx.delete(url).build().expect(202).execute().parseAsyncOperation();
    }

    public List<ImageInfo> listImages() {
        return ctx.get("images" + RECURSION_SUFFIX).build().execute().parseSync(new TypeReference<LxdResponse<List<ImageInfo>>>() {});
    }

    public AsyncOperation imageDelete(String imageFingerprint) {
        return ctx.delete(format("images/%s", imageFingerprint)).build().expect(202).execute().parseAsyncOperation();
    }

    public ImageInfo imageInfo(String imageFingerprint) {
        return ctx.get(format("images/%s", imageFingerprint)).build().execute().parseSync(new TypeReference<LxdResponse<ImageInfo>>() {});
    }

    public ImageAliasesEntry imageGetAlias(String aliasName) {
        return ctx.get(format("images/aliases/%s", aliasName)).build().execute().parseSync(new TypeReference<LxdResponse<ImageAliasesEntry>>() {});
    }
    
    private static final Logger logger = Logger.getLogger(DefaultLXDClient.class.getName());
}
