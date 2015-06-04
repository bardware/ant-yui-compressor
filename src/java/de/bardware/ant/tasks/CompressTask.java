package de.bardware.ant.tasks;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Mapper;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

/**
 * @author Simon Buckle
 * @author Parambir Singh
 * @author Bernhard Döbler
 */
public class CompressTask extends Task {

	private List<FileSet> filesets = new ArrayList<FileSet>( );
	private Mapper mapper;
	private int linebreak = -1;
	private boolean munge = true;
	private boolean preserveAllSemiColons = false;
	private boolean disableOptimizations = false;
	private boolean verbose = false;
	private boolean deleteOriginal = false;
	private String todir;
	private String charset = "UTF-8";

	public void addFileset( FileSet fileset ) {
		filesets.add( fileset );
	}

	public void addMapper( Mapper mapper ) {
		this.mapper = mapper;
	}

	public void setDisableOptimizations( boolean disableOptimizations ) {
		this.disableOptimizations = disableOptimizations;
	}

	public void setLinebreak( int linebreak ) {
		this.linebreak = linebreak;
	}

	public void setMunge( boolean munge ) {
		this.munge = munge;
	}

	public void setPreserveAllSemiColons( boolean preserveAllSemiColons ) {
		this.preserveAllSemiColons = preserveAllSemiColons;
	}

	public void setToDir( String todir ) {
		this.todir = todir;
	}

	public void setCharset( String charset ) {
		if( Charset.isSupported( charset ) ) {
			log( "Charset in: " + charset );
			this.charset = charset;
		}
		log( "Charset set: " + this.charset );
	}

	public void setVerbose( boolean verbose ) {
		this.verbose = verbose;
	}

	public void setDeleteOriginal( boolean deleteOrig ) {
		this.deleteOriginal = deleteOrig;
	}

	private void validateRequired( ) throws BuildException {
		StringBuilder errorString = new StringBuilder( );

		if( mapper == null )
			errorString.append( "Mapper property is required\n" );
		if( todir == null || "".equals( todir ) )
			errorString.append( "Output directory is not specified\n" );

		if( errorString.length( ) >0 ) {
			throw new BuildException( errorString.toString( ) );
		}
	}

	public void execute( ) throws BuildException {
		validateRequired( );

		Iterator<FileSet> iter = filesets.listIterator( );
		while( iter.hasNext( ) ) {
			FileSet fileset = iter.next( );
			DirectoryScanner scanner = fileset.getDirectoryScanner( getProject( ) );
			File dir = scanner.getBasedir( );

			String[] files = scanner.getIncludedFiles( );
			for( int i = 0; i < files.length; i++ ) {
				String[] output = mapper.getImplementation( ).mapFileName( files[i] );
				if( output != null ) {
					try {
						File originalFile = new File( dir, files[i] );
						if( originalFile.getName( ).endsWith( ".css" ) ) {
							compressCSS( originalFile, new File( todir, output[0] ) );
						} else {
							// treat all other file types as javascript files
							compressJS( originalFile, new File( todir, output[0] ) );
						}

						if( deleteOriginal ) {
							// delete the file just processed
							if( !originalFile.delete( ) ) {
								log( "Failed to delete file: " + originalFile.getName( ) );
							}
						}
					} catch( IOException io ) {
						log( "Failed to compress file: " + files[i] );
					}
				}
			}
		}
	}

	private void compressCSS( File source, File dest ) throws IOException {
		Reader in = null;
		Writer out = null;
		try {
			in = new BufferedReader( new InputStreamReader( new FileInputStream( source ), this.charset ) );
			out = new BufferedWriter( new OutputStreamWriter( new FileOutputStream( dest ), this.charset ) );
			log( "Compressing: " + source.getName( ) );

			CssCompressor compressor = new CssCompressor( in );
			compressor.compress( out, linebreak );
		} finally {
			if( in != null ) in.close( );
			if( out != null ) out.close( );
		}
	}

	private void compressJS( File source, File dest ) throws IOException {
		Reader in = null;
		Writer out = null;
		try {
			in = new BufferedReader( new InputStreamReader( new FileInputStream( source ), this.charset ) );
			out = new BufferedWriter( new OutputStreamWriter( new FileOutputStream( dest ), this.charset ) );
			log( "Compressing: " + source.getName( ) );

			JavaScriptCompressor compressor = new JavaScriptCompressor( in, new ErrorReporter( ) {

				public void warning( String message, String sourceName, int line, String lineSource, int lineOffset ) {
					// System.err.println("\n[WARNING] in " + this.val$localFilename);
					// source.getName()
					if ( line < 0 ) {
						log( message, Project.MSG_WARN );
					} else {
						log( line + ":" + lineOffset + ":" + message, Project.MSG_WARN );
					}
				}

				public void error( String message, String sourceName, int line, String lineSource, int lineOffset ) {
					if ( line < 0 ) {
						log( message, Project.MSG_ERR );
					} else {
						log( line + ":" + lineOffset + ":" + message, Project.MSG_ERR );
					}
					// log("Error: " + message, Project.MSG_ERR);
				}

				public EvaluatorException runtimeError( String message, String sourceName, int line, String lineSource, int lineOffset ) {
					error( message, sourceName, line, lineSource, lineOffset );
					return new EvaluatorException( message );
				}

			} );

			compressor.compress( out,
								linebreak,
								munge,
								verbose,
								preserveAllSemiColons,
								disableOptimizations );
		} finally {
			if( in != null ) in.close( );
			if( out != null ) out.close( );
		}
	}
}
