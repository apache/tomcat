#!/usr/local/bin/perl
#
#  Heuristically converts line endings to the current OS's preferred format
#  
#  All existing line endings must be identical (e.g. lf's only, or even
#  the accedental cr.cr.lf sequence.)  If some lines end lf, and others as
#  cr.lf, the file is presumed binary.  If the cr character appears anywhere
#  except prefixed to an lf, the file is presumed binary.  If there is no 
#  change in the resulting file size, or the file is binary, the conversion 
#  is discarded.
#  
#  Todo: Handle NULL stdin characters gracefully.
#

use IO::File;
use File::Find;

# The ignore list is '-' seperated, with this leading hyphen and
# trailing hyphens in ever concatinated list below.
$ignore = "-";

# Image formats
$ignore .= "gif-jpg-jpeg-png-ico-bmp-";

# Archive formats
$ignore .= "tar-gz-z-zip-jar-war-bz2-tgz-";

# Many document formats
$ignore .= "eps-psd-pdf-ai-";

# Some encodings
$ignore .= "ucs2-ucs4-";

# Some binary objects
$ignore .= "class-so-dll-exe-obj-a-o-lo-slo-sl-dylib-";

# Some build env files 
$ignore .= "mcp-xdc-ncb-opt-pdb-ilk-sbr-";

$preservedate = 1;

$forceending = 0;

$givenpaths = 0;

$notnative = 0;

while (defined @ARGV[0]) {
    if (@ARGV[0] eq '--touch') {
        $preservedate = 0;
    }
    elsif (@ARGV[0] eq '--nocr') {
        $notnative = -1;
    }
    elsif (@ARGV[0] eq '--cr') {
        $notnative = 1;
    }
    elsif (@ARGV[0] eq '--force') {
        $forceending = 1;
    }
    elsif (@ARGV[0] eq '--FORCE') {
        $forceending = 2;
    }
    elsif (@ARGV[0] =~ m/^-/) {
        die "What is " . @ARGV[0] . " supposed to mean?\n\n" 
	  . "Syntax:\t$0 [option()s] [path(s)]\n\n" . <<'OUTCH'
Where:	paths specifies the top level directory to convert (default of '.')
	options are;

	  --cr     keep/add one ^M
	  --nocr   remove ^M's
	  --touch  the datestamp (default: keeps date/attribs)
	  --force  mismatched corrections (unbalanced ^M's)
	  --FORCE  all files regardless of file name!

OUTCH
    }
    else {
        find(\&totxt, @ARGV[0]);
	print "scanned " . @ARGV[0] . "\n";
	$givenpaths = 1;
    }
    shift @ARGV;
}

if (!$givenpaths) {
    find(\&totxt, '.');
    print "did .\n";
}

sub totxt {
        $oname = $_;
	$tname = '.#' . $_;
        if (!-f) {
            return;
        }
	@exts = split /\./;
	if ($forceending < 2) {
            while ($#exts && ($ext = pop(@exts))) {
                if ($ignore =~ m|-$ext-|i) {
                    return;
                }
	    }
        }
	@ostat = stat($oname);
        $srcfl = new IO::File $oname, "r" or die;
	$dstfl = new IO::File $tname, "w" or die;
        binmode $srcfl; 
	if ($notnative) {
            binmode $dstfl;
	} 
	undef $t;
        while (<$srcfl>) { 
            if (s/(\r*)\n$/\n/) {
		$n = length $1;
		if (!defined $t) { 
		    $t = $n; 
		}
		if (!$forceending && (($n != $t) || m/\r/)) {
		    print "mismatch in " .$oname. ":" .$n. " expected " .$t. "\n";
		    undef $t;
		    last;
		}
	        elsif ($notnative > 0) {
                    s/\n$/\r\n/; 
                }
            }
	    print $dstfl $_; 
	}
	if (defined $t && (tell $srcfl == tell $dstfl)) {
	    undef $t;
	}
	undef $srcfl;
	undef $dstfl;
	if (defined $t) {
            unlink $oname or die;
            rename $tname, $oname or die;
            @anames = ($oname);
            if ($preservedate) {
                utime $ostat[9], $ostat[9], @anames;
            }
            chmod $ostat[2] & 07777, @anames;
            chown $ostat[5], $ostat[6], @anames;
            print "Converted file " . $oname . " to text in " . $File::Find::dir . "\n"; 
	}
	else {
	    unlink $tname or die;
	}
}
