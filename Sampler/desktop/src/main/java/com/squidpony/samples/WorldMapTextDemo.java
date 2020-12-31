package com.squidpony.samples;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.squidpony.samples.desktop.CustomConfig;
import squidpony.ArrayTools;
import squidpony.FakeLanguageGen;
import squidpony.StringKit;
import squidpony.squidgrid.gui.gdx.DefaultResources;
import squidpony.squidgrid.gui.gdx.FilterBatch;
import squidpony.squidgrid.gui.gdx.SColor;
import squidpony.squidgrid.gui.gdx.SparseLayers;
import squidpony.squidgrid.gui.gdx.SquidInput;
import squidpony.squidgrid.gui.gdx.SquidMouse;
import squidpony.squidgrid.gui.gdx.WorldMapView;
import squidpony.squidgrid.mapping.PoliticalMapper;
import squidpony.squidgrid.mapping.WorldMapGenerator;
import squidpony.squidmath.Coord;
import squidpony.squidmath.FastNoise;
import squidpony.squidmath.OrderedMap;
import squidpony.squidmath.StatefulRNG;

/**
 * Map generator that uses text to show features at a location as well as color.
 * Port of Zachary Carter's world generation technique, https://github.com/zacharycarter/mapgen
 * It seems to mostly work now, though it only generates one view of the map that it renders (but biome, moisture, heat,
 * and height maps can all be requested from it).
 */
public class WorldMapTextDemo extends ApplicationAdapter {
    public static final char[]  terrainChars = {
            '¿', //sand
            '„', //lush grass
            '♣', //jungle
            '‚', //barren grass
            '¥', //forest
            '¥', //forest
            '♣', //jungle
            '¥', //forest
            '‚', //barren grass
            '¤', //ice
            '.', //sand
            '∆', //rocky
            '~', //shallow
            '≈', //ocean
            ' ', //empty space
            //'■', //active city
            '□', //empty city


    };
//public static final char[]  terrainChars = {
//        '.', //sand
//        '"', //lush grass
//        '?', //jungle
//        '\'', //barren grass
//        '$', //forest
//        '$', //forest
//        '?', //jungle
//        '$', //forest
//        '‚', //barren grass
//        '*', //ice
//        '.', //sand
//        '^', //rocky
//        '~', //shallow
//        '~', //ocean
//        ' ', //empty space
//        //'■', //active city
//        '#', //empty city
//};
    //private static final int bigWidth = 314 * 3, bigHeight = 300;
//    private static final int bigWidth = 256, bigHeight = 256;
//    private static final int bigWidth = 1024, bigHeight = 512;
    private static final int bigWidth = 512, bigHeight = 256;
//    private static final int bigWidth = 2048, bigHeight = 1024;
    //private static final int bigWidth = 400, bigHeight = 400;
    private static final int cellWidth = 16, cellHeight = 16;
    private static final int shownWidth = 96, shownHeight = 48;
    private FilterBatch batch;
    private SparseLayers display;//, overlay;
    private SquidInput input;
    private Stage stage;
    private Viewport view;
    private StatefulRNG rng;
    private long seed;
    private Vector3 position, previousPosition, nextPosition;
    private WorldMapGenerator world;
    private WorldMapView wmv;
    private PoliticalMapper pm;
    private OrderedMap<Character, FakeLanguageGen> atlas;
    private OrderedMap<Coord, String> cities;
    //private WorldMapGenerator.EllipticalMap world;
    //private final float[][][] cloudData = new float[128][128][128];
    private long counter = 0;
    //private float nation = 0f;
    private long ttg = 0; // time to generate
    private float moveAmount = 0f;
    private static float black = SColor.FLOAT_BLACK,
            white = SColor.FLOAT_WHITE;
    // Biome map colors

    private static float ice = SColor.ALICE_BLUE.toFloatBits();
    private static float lightIce = white;

    private static float desert = SColor.floatGetI(248, 229, 180);

    private static float savanna = SColor.floatGetI(181, 200, 100);

    private static float tropicalRainforest = SColor.floatGetI(66, 123, 25);

    private static float tundra = SColor.floatGetI(151, 175, 159);

    private static float temperateRainforest = SColor.floatGetI(54, 113, 60);

    private static float grassland = SColor.floatGetI(169, 185, 105);

    private static float seasonalForest = SColor.floatGetI(100, 158, 75);

    private static float borealForest = SColor.floatGetI(75, 105, 45);

    private static float woodland = SColor.floatGetI(122, 170, 90);

    private static float rocky = SColor.floatGetI(171, 175, 145);

    private static float beach = SColor.floatGetI(255, 235, 180);

    private static float emptyColor = SColor.DB_INK.toFloatBits();

    // water colors
    private static float deepColor = SColor.floatGetI(0, 42, 88);
    private static float mediumColor = SColor.floatGetI(0, 89, 159);
    private static float shallowColor = SColor.floatGetI(0, 73, 137);
    private static float coastalColor = SColor.lightenFloat(shallowColor, 0.3f);
    private static float foamColor = SColor.floatGetI(61,  162, 215);

    private static final char[] BIOME_CHARS = new char[61];
    static {
        for (int i = 0; i < 61; i++) {
            BIOME_CHARS[i] = terrainChars[(int) WorldMapView.BIOME_TABLE[i]];
        }
    }
    
    @Override
    public void create() {
        batch = new FilterBatch();
        display = new SparseLayers(bigWidth, bigHeight, cellWidth, cellHeight, DefaultResources.getCrispSlabFamily());
        view = new StretchViewport(shownWidth * cellWidth, shownHeight * cellHeight);
        stage = new Stage(view, batch);
        seed = 1234567890L;
        rng = new StatefulRNG(seed);
//// you can use whatever map you have instead of fantasy_map.png, where white means land and black means water
//        Pixmap pix = new Pixmap(Gdx.files.internal("special/fantasy_map.png"));
//        final int bigWidth = pix.getWidth() / 4, bigHeight = pix.getHeight() / 4;
//        GreasedRegion basis = new GreasedRegion(bigWidth, bigHeight);
//        for (int x = 0; x < bigWidth; x++) {
//            for (int y = 0; y < bigHeight; y++) {
//                if(pix.getPixel(x * 4, y * 4) < 0) // only counts every fourth row and every fourth column
//                    basis.insert(x, y);
//            }
//        }
//        basis = WorldMapGenerator.MimicMap.reprojectToElliptical(basis);
//// at this point you could get the GreasedRegion as a String, and save the compressed output to a file:
//// Gdx.files.local("map.txt").writeString(LZSPlus.compress(basis.serializeToString()), false, "UTF16");
//// you could reload basis without needing the original map image with
//// basis = GreasedRegion.deserializeFromString(LZSPlus.decompress(Gdx.files.local("map.txt").readString("UTF16")));
//// it's also possible to store the compressed map as a String in code, but you need to be careful about escaped chars.
//        world = new WorldMapGenerator.LocalMimicMap(seed, basis, FastNoise.instance, 0.8);
//        pix.dispose();

        FastNoise noise = new FastNoise(0x1337BEEF, 2f, FastNoise.FOAM_FRACTAL, 2, 2.5f, 0.4f);
        world = new WorldMapGenerator.MimicMap(seed, noise, 0.8); // uses a map of Earth for land
//        world = new WorldMapGenerator.HyperellipticalMap(seed, bigWidth, bigHeight, WorldMapGenerator.DEFAULT_NOISE, 0.8);
        //world = new WorldMapGenerator.TilingMap(seed, bigWidth, bigHeight, WhirlingNoise.instance, 0.9);
        wmv = new WorldMapView(world);
        pm = new PoliticalMapper(FakeLanguageGen.SIMPLISH.word(rng, true));
        cities = new OrderedMap<>(96);
        atlas = new OrderedMap<>(80);
        position = new Vector3(bigWidth * cellWidth * 0.5f, bigHeight * cellHeight * 0.5f, 0);
        previousPosition = position.cpy();
        nextPosition = position.cpy();
        input = new SquidInput(new SquidInput.KeyHandler() {
            @Override
            public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
                switch (key) {
                    case SquidInput.ENTER:
                        seed = rng.nextLong();
                        generate(seed);
                        rng.setState(seed);
                        break;
                    case SquidInput.DOWN_ARROW:
                        position.add(0, 1, 0);
                        break;
                    case SquidInput.UP_ARROW:
                        position.add(0, -1, 0);
                        break;
                    case SquidInput.LEFT_ARROW:
                        position.add(-1, 0, 0);
                        break;
                    case SquidInput.RIGHT_ARROW:
                        position.add(1, 0, 0);
                        break;
                    case 'Q':
                    case 'q':
                    case SquidInput.ESCAPE: {
                        ((Lwjgl3Graphics)Gdx.graphics).getWindow().closeWindow();
                    }
                }
            }
        }, new SquidMouse(1, 1, bigWidth * cellWidth, bigHeight * cellHeight, 0, 0, new InputAdapter()
        {
            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                previousPosition.set(position);
                nextPosition.set(screenX, screenY, 0);
                stage.getCamera().unproject(nextPosition);
                nextPosition.set(MathUtils.round(nextPosition.x), MathUtils.round(nextPosition.y), nextPosition.z);
                counter = System.currentTimeMillis();
                moveAmount = 0f;
                return true;
            }
        }));
        generate(seed);
        rng.setState(seed);
        Gdx.input.setInputProcessor(input);
        display.setPosition(0, 0);
        stage.addActor(display);
    }

//    public void zoomIn() {
//        zoomIn(bigWidth >> 1, bigHeight >> 1);
//    }
//    public void zoomIn(int zoomX, int zoomY)
//    {
//        long startTime = System.currentTimeMillis();
//        world.zoomIn(1, zoomX, zoomY);
//        dbm.makeBiomes(world);
//        //counter = 0L;
//        ttg = System.currentTimeMillis() - startTime;
//    }
//    public void zoomOut()
//    {
//        zoomOut(bigWidth >>1, bigHeight >>1);
//    }
//    public void zoomOut(int zoomX, int zoomY)
//    {
//        long startTime = System.currentTimeMillis();
//        world.zoomOut(1, zoomX, zoomY);
//        dbm.makeBiomes(world);
//        //counter = 0L;
//        ttg = System.currentTimeMillis() - startTime;
//    }
    public void generate(final long seed)
    {
        long startTime = System.currentTimeMillis();
        System.out.println("Seed used: 0x" + StringKit.hex(seed) + "L");
        world.seedA = (int)(seed & 0xFFFFFFFFL);
        world.seedB = (int) (seed >>> 32);
        wmv.generate();
        wmv.show();
        atlas.clear();
        for (int i = 0; i < 64; i++) {
            atlas.put(ArrayTools.letterAt(i),
                    rng.getRandomElement(FakeLanguageGen.romanizedHumanLanguages).mix(rng.getRandomElement(FakeLanguageGen.romanizedHumanLanguages), rng.nextFloat()).removeAccents());
        }
        final char[][] political = pm.generate(world, atlas, 1.0);
        cities.clear();
        Coord[] points = world.landData
                .copy() // don't want to edit the actual land map
                .removeEdges() // don't want cities on the edge of the map
                .separatedRegionBlue(0.1, 500) // get 500 points in a regularly-tiling but unpredictable, sparse pattern
                .randomPortion(rng,112); // randomly select less than 1/4 of those points, breaking the pattern
        for (int i = 0; i < points.length; i++) {
            char p = political[points[i].x][points[i].y];
            if(p == '~' || p == '%')
                continue;
            FakeLanguageGen lang = atlas.get(p);
            if(lang != null)
            {
                cities.put(points[i], lang.word(rng, false).toUpperCase());
            }
        }
        //counter = 0L;
        ttg = System.currentTimeMillis() - startTime;
    }

    public void putMap() {
        // uncomment next line to generate maps as quickly as possible
        //generate(rng.nextLong());
//        ArrayTools.fill(display.backgrounds, -0x1.0p125F);
        ArrayTools.insert(wmv.getColorMap(), display.backgrounds, 0, 0);
        WorldMapGenerator.DetailedBiomeMapper dbm = wmv.getBiomeMapper();
        int hc, tc, codeA, codeB;
        float mix;
        int[][] heightCodeData = world.heightCodeData;
        //double xp, yp, zp;
        for (int y = 0; y < bigHeight; y++) {
            PER_CELL:
            for (int x = 0; x < bigWidth; x++) {
                hc = heightCodeData[x][y];
                if(hc == 1000)
                    continue;
                tc = dbm.heatCodeData[x][y];
                if(tc == 0)
                {
                    switch (hc)
                    {
                        case 0:
                        case 1:
                        case 2:
                            display.put(x, y, '≈', wmv.BIOME_DARK_COLOR_TABLE[30]);//SColor.darkenFloat(ice, 0.45f));
                            continue PER_CELL;
                        case 3:
                            display.put(x, y, '~', wmv.BIOME_DARK_COLOR_TABLE[24]);//SColor.darkenFloat(ice, 0.35f));
                            continue PER_CELL;
                        case 4:
                            display.put(x, y, '¤', wmv.BIOME_DARK_COLOR_TABLE[42]);//SColor.darkenFloat(ice, 0.25f));
                            continue PER_CELL;
                    }
                }
                switch (hc) {
                    case 0:
                    case 1:
                    case 2:
                        display.put(x, y, '≈', wmv.BIOME_COLOR_TABLE[44]);// SColor.lightenFloat(WorldMapView.foamColor, 0.3f));
                        break;
                    case 3:
                        display.put(x, y, '~', wmv.BIOME_COLOR_TABLE[43]);// SColor.lightenFloat(WorldMapView.foamColor, 0.3f));
                        break;
                    default: 
                        int bc = dbm.biomeCodeData[x][y];
                        codeB = dbm.extractPartB(bc);
                        codeA = dbm.extractPartA(bc);
                        mix = dbm.extractMixAmount(bc);
                        if(mix <= 0.5) 
                            display.put(x, y, BIOME_CHARS[codeA], SColor.contrastLuma(wmv.BIOME_COLOR_TABLE[codeB], wmv.BIOME_COLOR_TABLE[codeA]));
                        else
                            display.put(x, y, BIOME_CHARS[codeB], SColor.contrastLuma(wmv.BIOME_COLOR_TABLE[codeA], wmv.BIOME_COLOR_TABLE[codeB]));
                }
            }
        }
        for (int i = 0; i < cities.size(); i++) {
            Coord ct = cities.keyAt(i);
            String cname = cities.getAt(i);
            display.put(ct.x, ct.y, '□', SColor.SOOTY_WILLOW_BAMBOO);
//            display.put(ct.x, ct.y, '#', SColor.SOOTY_WILLOW_BAMBOO);
            display.put(ct.x - (cname.length() >> 1), ct.y - 1, cname, SColor.CW_FADED_YELLOW, SColor.SOOTY_WILLOW_BAMBOO);
        }
    }
    @Override
    public void render() {
        // standard clear the background routine for libGDX
        Gdx.gl.glClearColor(SColor.DB_INK.r, SColor.DB_INK.g, SColor.DB_INK.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        if(!nextPosition.epsilonEquals(previousPosition)) {
            moveAmount = (System.currentTimeMillis() - counter) * 0.001f;
            if (moveAmount <= 1f) {
                position.set(previousPosition).lerp(nextPosition, moveAmount);
                position.set(MathUtils.round(position.x), MathUtils.round(position.y), position.z);
            }
            else {
                previousPosition.set(position);
                nextPosition.set(position);
                nextPosition.set(MathUtils.round(nextPosition.x), MathUtils.round(nextPosition.y), nextPosition.z);
                moveAmount = 0f;
                counter = System.currentTimeMillis();
            }
        }
        stage.getCamera().position.set(position);
        // need to display the map every frame, since we clear the screen to avoid artifacts.
        putMap();
        Gdx.graphics.setTitle("Map! Took " + ttg + " ms to generate");

        // if we are waiting for the player's input and get input, process it.
        if (input.hasNext()) {
            input.next();
        }
        // stage has its own batch and must be explicitly told to draw().
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        view.update(width, height, true);
        view.apply(true);
    }
    public static CustomConfig config = new CustomConfig("WorldMapTextDemo"){
        {
            setTitle("SquidLib Demo: Earth-Mimic WorldMap With Text");
            setWindowedMode(shownWidth * cellWidth, shownHeight * cellHeight);
            useVsync(true);
            setIdleFPS(1);
        }
        @Override
        public ApplicationListener instantiate() {
            return new WorldMapTextDemo();
        }
    };
}
