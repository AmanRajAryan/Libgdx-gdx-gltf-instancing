package com.badlogic.mygame;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.g3d.shaders.DepthShader;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;

import java.nio.Buffer;
import java.nio.FloatBuffer;

public class MyGame extends ApplicationAdapter {

    public static String GLTF_FILE = "models/corn.gltf";
    public static String NODE_NAME = "cornstalk"; // "cornstalk"  "reeds"

    private static final float SEPARATION_DISTANCE = 1f; // min distance between instances
    private static final float AREA_LENGTH = 50.0f; // size of the (square) field
    private static final boolean AUTO_ROTATE = false;

    private SceneManager sceneManager;
    private SceneAsset sceneAsset;
    private Scene sceneCorn;
    private PerspectiveCamera camera;
    private Cubemap diffuseCubemap;
    private Cubemap environmentCubemap;
    private Cubemap specularCubemap;
    private Texture brdfLUT;
    private SceneSkybox skybox;
    private DirectionalLightEx light;
    private CameraInputController camController;
    private BitmapFont font;
    private SpriteBatch batch;
    private int instanceCount;
    boolean instancinEnabled = true;

    
    @Override
    public void create() {

        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        if (Gdx.gl30 == null) {
            throw new GdxRuntimeException("GLES 3.0 profile required for this programme.");
        }
        font = new BitmapFont();
        font.setOwnsTexture(false);
        batch = new SpriteBatch();

        // setup camera
        camera = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 800f;
        camera.position.set(-5, 2.5f, -5);
        camera.lookAt(10, 1.5f, 10);
        camera.update();

        // create scene manager
        // but use our own shader providers
        sceneManager =
                new SceneManager(
                        new MyPBRShaderProvider(),
                        new MyPBRDepthShaderProvider(new DepthShader.Config()));
        sceneManager.setCamera(camera);

        camController = new CameraInputController(camera);
        Gdx.input.setInputProcessor(camController);

        // setup light
        light = new DirectionalLightEx();

        light.direction.set(1, -3, -1).nor();
        light.color.set(Color.WHITE);
        light.intensity = 0.81f;
        sceneManager.environment.add(light);

        // setup quick IBL (image based lighting)
        IBLBuilder iblBuilder = IBLBuilder.createOutdoor(light);
        environmentCubemap = iblBuilder.buildEnvMap(1024);
        diffuseCubemap = iblBuilder.buildIrradianceMap(256);
        specularCubemap = iblBuilder.buildRadianceMap(10);
        iblBuilder.dispose();

        // This texture is provided by the library, no need to have it in your assets.
        brdfLUT = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));

        sceneManager.setAmbientLight(0.5f);
        sceneManager.environment.set(
                new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
        sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));
        sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));

        // setup skybox
        skybox = new SceneSkybox(environmentCubemap);
        sceneManager.setSkyBox(skybox);

        sceneAsset = new GLTFLoader().load(Gdx.files.internal(GLTF_FILE));

        // extract the model to instantiate
        sceneCorn = new Scene(sceneAsset.scene, NODE_NAME);
        if (sceneCorn.modelInstance.nodes.size == 0) {
            Gdx.app.error("GLTF load error: node not found", NODE_NAME);
            Gdx.app.exit();
        }
        sceneManager.addScene(sceneCorn);

        if (instancinEnabled) {
            // instancing below
            // instancing below

            // assumes the instance has one node,  and the meshPart covers the whole mesh
            for (int i = 0; i < sceneCorn.modelInstance.nodes.first().parts.size; i++) {
                Mesh mesh = sceneCorn.modelInstance.nodes.first().parts.get(i).meshPart.mesh;
                setupInstancedMesh(mesh);
            }
        } else {
            // normal way of adding scene
            // normal way of adding scene
            // normal way of adding scene
            for (int i = 0; i < 1670; ++i) {
                Scene testCornScene = new Scene(sceneAsset.scene, NODE_NAME);
                testCornScene.modelInstance.transform.translate(
                        MathUtils.random(0, 50), 0, MathUtils.random(0, 50));

                sceneManager.addScene(testCornScene);
            }
        }
    }

    private void setupInstancedMesh(Mesh mesh) {

        // generate instance data

        // generate a random poisson distribution of instances over a rectangular area, meaning
        // instances are never too close together
        PoissonDistribution poisson = new PoissonDistribution();
        Rectangle area = new Rectangle(1, 1, AREA_LENGTH, AREA_LENGTH);
        Array<Vector2> points = poisson.generatePoissonDistribution(SEPARATION_DISTANCE, area);
        instanceCount = points.size;

        // add 4 floats per instance
        mesh.enableInstancedRendering(
                true,
                instanceCount,
                new VertexAttribute(VertexAttributes.Usage.Position, 4, "i_offset"));

        // Create offset FloatBuffer that will contain instance data to pass to shader
        FloatBuffer offsets = BufferUtils.newFloatBuffer(instanceCount * 4);
        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        Gdx.app.log("FloatBuffer: isDirect()", "" + offsets.isDirect()); // false = teaVM for now
        Gdx.app.log("Application: Type()", "" + Gdx.app.getType());

        // fill instance data buffer
        for (Vector2 point : points) {
            float angle =
                    MathUtils.random(
                            0.0f, (float) Math.PI * 2.0f); // random rotation around Y (up) axis
            float scaleY = MathUtils.random(0.8f, 2f); // vary scale in up direction +/- 20%

            offsets.put(new float[] {point.x, scaleY, point.y, angle}); // x, y-scale, z, y-rotation
        }

        ((Buffer) offsets).position(0);
        mesh.setInstanceData(offsets);
    }

    @Override
    public void render() {
        if (AUTO_ROTATE)
            sceneCorn.modelInstance.transform.rotate(Vector3.Y, Gdx.graphics.getDeltaTime() * 45f);

        camController.update();
        sceneManager.update(Gdx.graphics.getDeltaTime());

        ScreenUtils.clear(Color.TEAL, true);
        // Gdx.app.log("render frame", "");
        sceneManager.render();

        int fps = (int) (1f / Gdx.graphics.getDeltaTime());
        batch.begin();
        font.draw(
                batch, "Instanced rendering demo (1: toggle instances, 2: toggle decals)", 20, 110);
        font.draw(batch, "Instances: " + instanceCount, 20, 80);
        font.draw(batch, "Vertices/instance: " + countVertices(sceneCorn.modelInstance), 20, 50);
        font.draw(batch, "FPS: " + fps, 20, 20);

        batch.end();
    }

    private int countVertices(ModelInstance instance) {
        int count = 0;
        for (int i = 0; i < instance.nodes.first().parts.size; i++) {
            count += instance.nodes.first().parts.get(i).meshPart.mesh.getNumVertices();
        }
        return count;
    }

    @Override
    public void resize(int width, int height) {
        // Resize your screen here. The parameters represent the new window size.
        sceneManager.updateViewport(width, height);
    }

    @Override
    public void dispose() {

        sceneManager.dispose();
        sceneAsset.dispose();
        environmentCubemap.dispose();
        diffuseCubemap.dispose();
        specularCubemap.dispose();
        brdfLUT.dispose();
        skybox.dispose();
    }
}
