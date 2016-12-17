A number of the test files in this directory specify conflicting encoding
in the BOM and and in the XML prolog. The rules for determining the actual
encoding are as follows:

1. If there is a BOM, use the encoding defined by the BOM.

2. If there is no BOM but there is an XML prolog, use the encoding defined by
   the XML prolog.

3. If there is no BOM and no XML prolog, use UTF-8.


Notes:

1. The svn:mime-type property has been set correctly for all files.

2. For files in UTF-8 encoding, the svn:eol-style property has been set to
   native. Some svn clients do not seem to be able to handle setting that
   property on files using UTF-16LE or UTF16-BE. For these files, the
   line-ending used is CRLF (Windows) since that is where they were created.

2. Notepad++ is a useful text editor for working with these files since it
   provides explicit control of encoding and BOM used.