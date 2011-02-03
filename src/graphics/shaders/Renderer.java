/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package graphics.shaders;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

class Renderer implements GLSurfaceView.Renderer {
	/******************************
	 * PROPERTIES
	 ******************************/
	Activity activity;

	private static final int FLOAT_SIZE_BYTES = 4;
	private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 3 * FLOAT_SIZE_BYTES;
	private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
	private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

	// shader constants
	private final int GOURAUD_SHADER = 0;
	private final int PHONG_SHADER = 1;
	private final int NORMALMAP_SHADER = 2;
	//private static final int GOURAUD_SHADER = 0;

	// array of shaders
	Shader _shaders[] = new Shader[3];
	private int _currentShader;

	/** Shader code **/
	private int[] vShaders;
	private int[] pShaders;

	// Shaders for simple texturing (load from resources)

	private final String _basicVShader =
		"uniform mat4 uMVPMatrix;\n" +
		"attribute vec4 aPosition;\n" +
		"void main() {\n" +
		"  gl_Position = uMVPMatrix * aPosition;" +// * vec3(1.0, 1.0, 1.0);\n" +
		"}\n";

	private final String _basicTexturedPShader =
		"precision mediump float;\n" +
		"void main() {\n" +
		"  gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);\n" +
		"}\n";

	private final String mFragmentShader =
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform sampler2D sTexture;\n" +
		"void main() {\n" +
		"  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
		"}\n";

	// object constants
	private final int OCTAHEDRON = 0;
	//private final int PHONG_SHADER = 1;
	//private final int NORMALMAP_SHADER = 2;

	// The objects
	Object3D[] _objects = new Object3D[1];

	// current object
	private int _currentObject;



	private final float[] mTriangleVerticesData = {
			// X, Y, Z, U, V
			-1.0f, -0.5f, 0, -0.5f, 0.0f,
			1.0f, -0.5f, 0, 1.5f, -0.0f,
			0.0f,  1.11803399f, 0, 0.5f,  1.61803399f };

	private FloatBuffer mTriangleVertices;

	// Modelview/Projection matrices
	private float[] mMVPMatrix = new float[16];
	private float[] mProjMatrix = new float[16];
	private float[] mMMatrix = new float[16];
	private float[] mVMatrix = new float[16];

	private int mProgram;
	private int mTextureID;
	private int muMVPMatrixHandle;
	private int maPositionHandle;
	private int maTextureHandle;

	private Context mContext;
	private static String TAG = "GLES20TriangleRenderer";

	/***************************
	 * CONSTRUCTOR(S)
	 **************************/
	public Renderer(Context context) {

		mContext = context;
		/*mTriangleVertices = ByteBuffer.allocateDirect(mTriangleVerticesData.length
		 * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVertices.put(mTriangleVerticesData).position(0);*/

		// setup all the shaders
		vShaders = new int[3];
		pShaders = new int[3];
		// basic
		vShaders[0] = R.raw.vshader_basic;
		pShaders[0] = R.raw.pshader_basic;


		// Create some objects...
		// Octahedron - WORKS!
		try {
			_objects[0] = new Object3D(R.raw.octahedron, false, context);
		} catch (Exception e) {
			showAlert("" + e.getMessage());
		}
		_currentObject = this.OCTAHEDRON;
	}

	/*****************************
	 * GL FUNCTIONS
	 ****************************/
	/*
	 * Called on every frame
	 */
	public void onDrawFrame2(GL10 glUnused) {
		// Ignore the passed-in GL10 interface, and use the GLES20
		// class's static methods instead.

		// clear to black
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

		// the current shader
		Shader shader = _shaders[this._currentShader];
		GLES20.glUseProgram(shader.get_program());
		checkGlError("glUseProgram");

		// Some rotation stuff
		long time = SystemClock.uptimeMillis() % 4000L;
		float angle = 0.090f * ((int) time);
		Matrix.setRotateM(mMMatrix, 0, angle, 0, 0, 1.0f);
		Matrix.multiplyMM(mMVPMatrix, 0, mVMatrix, 0, mMMatrix, 0);
		Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mMVPMatrix, 0);

		// the modelview/projection matrix
		GLES20.glUniformMatrix4fv(shader.getMuMVPMatrixHandle(), 1, false, mMVPMatrix, 0);

		// Draw the current object
		//_objects[this._currentObject].draw(shader);

		/* GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maPosition");
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");*/



		// draw the mesh (triangle arrays)
		//_objects[this._currentObject].drawMesh();
		//GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
		//checkGlError("glDrawArrays");
	}

	public void onDrawFrame(GL10 glUnused) {
		// Ignore the passed-in GL10 interface, and use the GLES20
		// class's static methods instead.
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

		// the current shader
		Shader shader = _shaders[this._currentShader]; // PROBLEM!

		GLES20.glUseProgram(mProgram);
		checkGlError("glUseProgram");

		// GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		// GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);

		// NEW SHADER
		/*mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(shader.getMaPositionHandle(), 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maPosition");
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glEnableVertexAttribArray(shader.getMaPositionHandle());
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        GLES20.glVertexAttribPointer(shader.getMaTextureHandle(), 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(shader.getMaTextureHandle());
        checkGlError("glEnableVertexAttribArray maTextureHandle");*/

		// ORIGINAL SHADER
		/*mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(this.maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maPosition");
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glEnableVertexAttribArray(this.maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        /*GLES20.glVertexAttribPointer(this.maTextureHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");*/


		//GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);

		// draw the object
		//_objects[this._currentObject].draw(this.maPositionHandle);


		Log.d("onDrawFrame", "1");



		/*** DRAWING OBJECT **/
		// the vertex buffer
		Mesh mesh = _objects[this._currentObject].getMesh();
		FloatBuffer vb = mesh.get_vb();
		float[] vertices = mesh.get_vertices();

		ShortBuffer ib = mesh.get_ib();
		short[] indices = mesh.get_indices();

		int lenV = vertices.length;
		//for (int i = 0; i < lenV; i++)
			Log.d("Printing vb:", "I: " + 0 + "; " + vb.capacity());

		// Do binding and whatnot
		// generate buffers first
		/*int buffers[] = new int[2];
		GLES20.glGenBuffers(2, buffers, 0);

		// bind vertex buffer
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
				vertices.length * FLOAT_SIZE_BYTES,
				vb,
				GLES20.GL_STATIC_DRAW);

		// for indices
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[1]);
		GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
				indices.length * 2,
				ib,
				GLES20.GL_STATIC_DRAW);

		Log.d("onDrawFrame", "Between");
		
		// attrib pointer for vertex buffer
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
		GLES20.glEnableVertexAttribArray(this.maPositionHandle);
		GLES20.glVertexAttribPointer(this.maPositionHandle, vertices.length, GLES20.GL_FLOAT, false,
				TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vb);

		GLES20.glBindAttribLocation(mProgram, 0, "aPosition");
		
		Log.d("onDrawFrame", "Almost");
		
		// Draw using index buffer
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[1]);
		GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, ib);
		checkGlError("glDrawElements");*/
		
		vb.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(this.maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vb);
        //checkGlError("glVertexAttribPointer maPosition");
        //mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glEnableVertexAttribArray(this.maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        
        /*mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maPosition");
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");*/
        
        
		/** END DRAWING OBJECT ***/

		// MODELVIEW MATRIX

		Log.d("onDrawFrame", "2");
		
		long time = SystemClock.uptimeMillis() % 4000L;
		float angle = 0.090f * ((int) time);
		Matrix.setRotateM(mMMatrix, 0, angle, 0, 0, 1.0f);
		Matrix.multiplyMM(mMVPMatrix, 0, mVMatrix, 0, mMMatrix, 0);
		Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mMVPMatrix, 0);

		GLES20.glUniformMatrix4fv(this.muMVPMatrixHandle, 1, false, mMVPMatrix, 0);

		Log.d("onDrawFrame", "3");
		
		
		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.length);
		//GLES20.glUseProgram(0);
	}

	/*
	 * Called when viewport is changed
	 * @see android.opengl.GLSurfaceView$Renderer#onSurfaceChanged(javax.microedition.khronos.opengles.GL10, int, int)
	 */
	public void onSurfaceChanged(GL10 glUnused, int width, int height) {
		// Ignore the passed-in GL10 interface, and use the GLES20
		// class's static methods instead.
		GLES20.glViewport(0, 0, width, height);
		float ratio = (float) width / height;
		Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 0.1f, 10f);
	}

	/**
	 * Initialization function
	 */
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
		// Ignore the passed-in GL10 interface, and use the GLES20
		// class's static methods instead.

		// Generate all the shader programs
		// initialize shaders - PROBLEM!
		try {
			//_shaders[0] = new Shader(_basicVShader, _basicTexturedPShader);
		} catch (Exception e) {
			//showAlert("Setup Shader");
		}
		// set current shader
		_currentShader = this.GOURAUD_SHADER;


		mProgram = createProgram(_basicVShader, _basicTexturedPShader);
		if (mProgram == 0) {
			return;
		}
		maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
		checkGlError("glGetAttribLocation aPosition");
		if (maPositionHandle == -1) {
			throw new RuntimeException("Could not get attrib location for aPosition");
		}
		/*maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }*/

		muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
		checkGlError("glGetUniformLocation uMVPMatrix");
		if (muMVPMatrixHandle == -1) {
			throw new RuntimeException("Could not get attrib location for uMVPMatrix");
		}

		/*
		 * Create our texture(s). This has to be done each time the
		 * surface is created.
		 */
		//if ( _objects[this._currentObject].hasTexture())
		//_objects[this._currentObject].setupTexture(mContext);
		//for (int i = 0; i < _objects.length; i++)
		//_objects[i].setupTexture(mContext);

		/*int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        mTextureID = textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_REPEAT);

        InputStream is = mContext.getResources()
            .openRawResource(R.raw.robot);
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeStream(is);
        } finally {
            try {
                is.close();
            } catch(IOException e) {
                // Ignore.
            }
        }

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();*/

		// set the view matrix
		Matrix.setLookAtM(mVMatrix, 0, 0, 0, -5, 5.0f, 0f, 0f, 0f, 1.0f, 0.0f);
	}

	/**************************
	 * OTHER METHODS
	 *************************/

	/**
	 * Changes the shader based on menu selection
	 * @param represents the other shader 
	 */
	public void setShader(int shader) {
		_currentShader = shader;
	}

	/**
	 * Changes the object based on menu selection
	 * @param represents the other object 
	 */
	public void setObject(int object) {
		_currentObject = object;

		// setup texture?
		_objects[_currentObject].setupTexture(mContext);
	}

























	private int loadShader(int shaderType, String source) {
		int shader = GLES20.glCreateShader(shaderType);
		if (shader != 0) {
			GLES20.glShaderSource(shader, source);
			GLES20.glCompileShader(shader);
			int[] compiled = new int[1];
			GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
			if (compiled[0] == 0) {
				Log.e(TAG, "Could not compile shader " + shaderType + ":");
				Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
				GLES20.glDeleteShader(shader);
				shader = 0;
			}
		}
		return shader;
	}

	private int createProgram(String vertexSource, String fragmentSource) {
		int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
		if (vertexShader == 0) {
			return 0;
		}

		int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
		if (pixelShader == 0) {
			return 0;
		}

		int program = GLES20.glCreateProgram();
		if (program != 0) {
			GLES20.glAttachShader(program, vertexShader);
			checkGlError("glAttachShader");
			GLES20.glAttachShader(program, pixelShader);
			checkGlError("glAttachShader");
			GLES20.glLinkProgram(program);
			int[] linkStatus = new int[1];
			GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
			if (linkStatus[0] != GLES20.GL_TRUE) {
				Log.e(TAG, "Could not link program: ");
				Log.e(TAG, GLES20.glGetProgramInfoLog(program));
				GLES20.glDeleteProgram(program);
				program = 0;
			}
		}
		return program;
	}

	// debugging opengl
	private void checkGlError(String op) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(TAG, op + ": glError " + error);
			throw new RuntimeException(op + ": glError " + error);
		}
	}


	// debugging
	private void showAlert(String alert2) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this.mContext);
		builder.setMessage(alert2)
		.setCancelable(false)
		.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			// Just close the activity
			public void onClick(DialogInterface dialog, int id) {
				//Renderer.this.mContext.finish();
			}
		});
		/*.setNegativeButton("No", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	                dialog.cancel();
    	           }
    	       });*/
		AlertDialog alert = builder.create();
		alert.show();
	}

}