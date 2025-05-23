#!/usr/bin/perl

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# -----------------------------------------------------------------------------
# Merge the MIME type definitions contained in the
# file mime.types from the httpd project into Tomcat web.xml.
# -----------------------------------------------------------------------------

# The script uses two mime type lists to describe
# the merging between httpd and Tomcat mime types.
#
# - %TOMCAT_ONLY: Additional extensions for Tomcat that do not exist in httpd
# - %TOMCAT_KEEP: Mime type differences for common extensions where we stick to
#    the Tomcat definition

# The script checks consistency between Tomcat and httpd according
# to the lists TOMCAT_ONLY and TOMCAT_KEEP and generates a new web.xml:
#
# A) Additional extensions in Tomcat which are not part of TOMCAT_ONLY
#    are logged. They will be removed in the generated new web.xml.
#    If you want to keep them, add them to TOMCAT_ONLY and run the
#    script again. If you want to remove them, commit the generated
#    new web.xml.
# B) Mime type differences for the same extension between httpd
#    and Tomcat that are not part of TOMCAT_KEEP are logged.
#    They will be overwritten with the httpd definition in the generated
#    new web.xml. If you want to keep their Tomcat definition, add them
#    to TOMCAT_KEEP and run the script again. If you want to use the
#    definitions from httpd, commit the generated new web.xml.
# C) Additional extensions in httpd are logged. The script outputs a
#    merged web.xml, which already includes all those additional
#    extensions. If you want to keep them, commit the generated
#    new web.xml.
# D) If the extensions are not sorted alphabetically, a message is logged.
#    The generated web.xml will always be sorted alphabetically.
#    If you want to keep the alphabetical sort order, commit the generated
#    new web.xml.

use strict;
use locale;
use POSIX qw(locale_h);
use Getopt::Std;

################### BEGIN VARIABLES WHICH MUST BE MAINTAINED #####################

# Script version, printed via getopts with "--version"
our $VERSION = '1.2';

# Locale used via LC_COLLATE when sorting extensions
my $LOCALE  = 'en.UTF-8';

# Mime types that are part of the Tomcat
# configuration, but missing from httpd

my %TOMCAT_ONLY = qw(
    abs audio/x-mpeg
    aim application/x-aim
    anx application/annodex
    art image/x-jg
    avx video/x-rad-screenplay
    axa audio/annodex
    axv video/annodex
    body text/html
    dib image/bmp
    dv video/x-dv
    gz application/x-gzip
    htc text/x-component
    jsf text/plain
    jspf text/plain
    m4b audio/mp4
    m4r audio/mp4
    mp1 audio/mpeg
    mpa audio/mpeg
    mac image/x-macpaint
    mpega audio/x-mpeg
    mpv2 video/mpeg2
    pict image/pict
    pnt image/x-macpaint
    qti image/x-quicktime
    qtif image/x-quicktime
    shtml text/x-server-parsed-html
    ulw audio/basic
    z application/x-compress
);

# Mime types, that are defined differently
# in Tomcat than in httpd

my %TOMCAT_KEEP = qw(
    cdf application/x-cdf
    class application/java
    exe application/octet-stream
    flac audio/flac
    m4v video/mp4
    mif application/x-mif
    pct image/pict
    pic image/pict
    pls audio/x-scpls
);

################### END VARIABLES WHICH MUST BE MAINTAINED #####################

# Global data variables
# Mime type definitions from httpd
my %httpd;
# Mime type definitions from Tomcat
my %tomcat;
# Comments found when parsing mime type definitions
my %tomcat_comments;
# Is the whole mime type commented out?
my %tomcat_commented;
# List of extensions found in the original order
my @tomcat_extensions;
# Text in web.xml before and after the mime-type definitions
my $tomcat_pre; my $tomcat_post;


# Helper variables
my $i;
my $line;
my $mimetype;
my @extensions;
my $extension;
my $type;
my $comment;
my $commented;
my $msg;
my $previous;
my $current;
# File handles
my $mimetypes_fh;
my $webxml_fh;
my $output_fh;


# Usage/Help
sub HELP_MESSAGE {
    my $fh = shift;
    print $fh "Usage:: $0 -m MIMEFILE -i INPUTFILE -o OUTPUTFILE\n";
    print $fh "           MIMEFILE:   path to mime.types from the httpd project\n";
    print $fh "           INPUTFILE:  path to existing web.xml, which will be checked\n";
    print $fh "           OUTPUTFILE: path to the new (generated) web.xml. Any existing\n";
    print $fh "                       file will be overwritten.\n";
}


# Parse arguments:
# -m: mime.types file (httpd) to use
# -i: input web.xml file to check
# -o: output web.xml file (gets generated and overwritten)

$Getopt::Std::STANDARD_HELP_VERSION = 1;
our ($opt_m, $opt_i, $opt_o);
getopts('m:i:o:');


# Check whether mandatory arguments are given
if ($opt_m eq '' || $opt_i eq '' || $opt_o eq '') {
    HELP_MESSAGE(*STDOUT);
    exit 1;
}


# Switch locale for alphabetical ordering
setlocale(LC_COLLATE, $LOCALE);

print STDERR "INFO Using lists TOMCAT_KEEP and TOMCAT_ONLY defined in this script.\n";

# Check whether TOMCAT_ONLY and TOMCAT_KEEP are disjoint
for $extension (sort keys %TOMCAT_ONLY) {
    if (exists($TOMCAT_KEEP{$extension})) {
        push(@extensions, ($extension));
    }
}
if (@extensions > 0) {
    print STDERR "FATAL TOMCAT_ONLY and TOMCAT_KEEP must be disjoint.\n";
    print STDERR "FATAL Common entries are: " . join(', ', @extensions) . " - Aborting!\n";
    exit 6;
}

# Read and parse httpd mime.types, build up hash extension->mime-type
open($mimetypes_fh, '<', $opt_m) or die "Could not open file '$opt_m' for read - Aborting!";
while (<$mimetypes_fh>) {
    chomp($_);
    $line = $_;
    $line =~ s/#.*//;
    $line =~ s/^\s+//;
    if ($line ne '') {
        ($mimetype, @extensions) = split(/\s+/, $line);
        if (@extensions > 0) {
            for $extension (@extensions) {
                $httpd{$extension} = $mimetype;
            }
        } else {
            print STDERR "WARN mime.types line ignored: $_\n";
        }
    }
}
close($mimetypes_fh);

# Read and parse web.xml, build up hash extension->mime-type
# and store the file parts form before and after mime mappings.
open($webxml_fh, '<', $opt_i) or die "Could not open file '$opt_i' for read - Aborting!";

# Skip and record all lines before the first mime type definition.
# Because of comment handling we need to read one line ahead.
$line = '';
while (<$webxml_fh>) {
    if ($_ !~ /<mime-mapping>/) {
        $tomcat_pre .= $line;
    } else {
        last;
    }
    $line = $_;
}

$commented = 0;
# If the previous line was start of a comment
# set marker, else add it to pre.
if ($line =~ /^\s*<!--[^>]*$/) {
    $commented = 1;
} else {
    $tomcat_pre .= $line;
}

# Now we parse blocks of the form:
#    <mime-mapping>
#        <extension>abs</extension>
#        <mime-type>audio/x-mpeg</mime-type>
#    </mime-mapping>
# Optional single comment lines directly after "<mime-mapping>"
# are allowed. The whole block is also allowed to be commented out.

while ($_ =~ /^\s*<mime-mapping>\s*$/) {
    $_ = <$webxml_fh>;
    chomp($_);
    $comment = '';
    if ($_ =~ /^\s*<!--([^>]*)-->\s*$/) {
        $comment = $1;
        $_ = <$webxml_fh>;
        chomp($_);
    }
    if ($_ =~ /^\s*<extension>([^<]*)<\/extension>\s*$/ ) {
        $extension = $1;
        $extension =~ s/^\s+//;
        $extension =~ s/\s+$//;
    } else {
        print STDERR "ERROR Parse error in Tomcat mime-mapping line $.\n";
        print STDERR "ERROR Expected <extension>...</extension>', got '$_' - Aborting!\n";
        close($webxml_fh);
        exit 2;
    }
    $_ = <$webxml_fh>;
    chomp($_);
    if ($_ =~ /^\s*<mime-type>([^<]*)<\/mime-type>\s*$/ ) {
        $type = $1;
        $type =~ s/^\s+//;
        $type =~ s/\s+$//;
        if (exists($tomcat{$extension}) && $tomcat{$extension} ne $type) {
            print STDERR "WARN MIME mapping redefinition detected!\n";
            print STDERR "WARN Kept '$extension' -> '$tomcat{$extension}'\n";
            print STDERR "WARN Ignored '$extension' -> '$type'\n";
        } else {
            $tomcat{$extension} = $type;
            if ($comment ne '') {
                $tomcat_comments{$extension} = $comment;
            }
            if ($commented) {
                $tomcat_commented{$extension} = 1;
            }
            push(@tomcat_extensions, $extension);
        }
    } else {
        print STDERR "ERROR Parse error in Tomcat mime-mapping line $.\n";
        print STDERR "ERROR Expected <mime-type>...</mime-type>', got '$_' - Aborting!\n";
        close($webxml_fh);
        exit 3;
    }
    $_ = <$webxml_fh>;
    chomp($_);
    if ($_ !~ /^\s*<\/mime-mapping>\s*$/) {
        print STDERR "ERROR Parse error in Tomcat mime-mapping line $.\n";
        print STDERR "ERROR Expected '</mime-mapping>', got '$_' - Aborting!\n";
        close($webxml_fh);
        exit 4;
    }
    $_ = <$webxml_fh>;
    # Check for comment closure
    if ($commented && $_ =~ /^[^<]*-->\s*$/) {
        $commented = 0;
        $_ = <$webxml_fh>;
    }
    # Check for comment opening
    if ($_ =~ /^\s*<!--[^>]*$/) {
        $commented = 1;
        $line = $_;
        $_ = <$webxml_fh>;
    }
}

# Add back the last comment line already digested
if ($commented) {
    $tomcat_post = $line;
}

# Read and record the remaining lines
$tomcat_post .= $_;
while (<$webxml_fh>) {
    if ($_ =~ /<mime-mapping>/) {
        print STDERR "ERROR mime-mapping blocks are not consecutive\n";
        print STDERR "ERROR See line $. in $opt_i - Aborting!\n";
        close($webxml_fh);
        exit 5;
    }
    $tomcat_post .= $_;
}

close($webxml_fh);


# Look for extensions in TOMCAT_ONLY.
# Abort if it already exists in mime.types.
# Warn if they are no longer existing in web.xml.
for $extension (sort keys %TOMCAT_ONLY) {
    if (exists($httpd{$extension})) {
        if ($httpd{$extension} eq $TOMCAT_ONLY{$extension}) {
            print STDERR "FATAL Consistent definition for '$extension' -> '$TOMCAT_ONLY{$extension}' exists in mime.types.\n";
            print STDERR "FATAL You must remove '$extension' from TOMCAT_ONLY - Aborting!\n";
            exit 7;
        } else {
            print STDERR "FATAL Definition '$extension' -> '$httpd{$extension}' exists in mime.types but\n";
            print STDERR "FATAL differs from '$extension' -> '$TOMCAT_ONLY{$extension}' in TOMCAT_ONLY.\n";
            print STDERR "FATAL You must either remove '$extension' from TOMCAT_ONLY to keep the mime.types variant,\n";
            print STDERR "FATAL or move it to TOMCAT_KEEP to overwrite the mime.types variant - Aborting!\n";
            exit 8;
        }
    }
    if (!exists($tomcat{$extension})) {
        print STDERR "WARN Additional extension '$extension' allowed by TOMCAT_ONLY, but not found in web.xml\n";
        print STDERR "WARN Definition '$extension' -> '$TOMCAT_ONLY{$extension}' will be added again to generated web.xml.\n";
        print STDERR "WARN Consider remove it from TOMCAT_ONLY if you do not want to add back this extension.\n";
    }
}


# Look for extensions in TOMCAT_KEEP.
# Abort if they do not exist in mime.types or have the same definition there.
# Warn if they are no longer existing in web.xml.
for $extension (sort keys %TOMCAT_KEEP) {
    if (exists($httpd{$extension})) {
        if ($httpd{$extension} eq $TOMCAT_KEEP{$extension}) {
            print STDERR "FATAL Consistent definition for '$extension' -> '$TOMCAT_KEEP{$extension}' exists in mime.types.\n";
            print STDERR "FATAL You must remove '$extension' from TOMCAT_KEEP - Aborting!\n";
            exit 9;
        }
    } else {
        print STDERR "FATAL Definition '$extension' -> '$TOMCAT_KEEP{$extension}' does not exist in mime.types,\n";
        print STDERR "FATAL so you must move it from TOMCAT_KEEP to TOMCAT_ONLY - Aborting!\n";
        exit 10;
    }
    if (!exists($tomcat{$extension})) {
        print STDERR "WARN Additional extension '$extension' allowed by TOMCAT_KEEP, but not found in web.xml\n";
        print STDERR "WARN Definition '$extension' -> '$TOMCAT_KEEP{$extension}' will be added again to generated web.xml.\n";
        print STDERR "WARN Consider removing it from TOMCAT_KEEP if you do not want to add back this extension.\n";
    }
}


# Look for extensions existing for Tomcat but not for httpd.
# Log them if they are not in TOMCAT_ONLY
for $extension (@tomcat_extensions) {
    if (!exists($httpd{$extension})) {
        if (!exists($TOMCAT_ONLY{$extension})) {
            print STDERR "WARN Extension '$extension' found in web.xml but not in mime.types is missing from TOMCAT_ONLY.\n";
            print STDERR "WARN Definition '$extension' -> '$tomcat{$extension}' will be removed from generated web.xml.\n";
            print STDERR "WARN Consider adding it to TOMCAT_ONLY if you want to keep this extension.\n";
        } elsif ($tomcat{$extension} ne $TOMCAT_ONLY{$extension}) {
            print STDERR "WARN Additional extension '$extension' allowed by TOMCAT_ONLY list, but has new definition.\n";
            print STDERR "WARN Definition '$extension' -> '$tomcat{$extension}' will be replaced" .
                         " by '$extension' -> '$TOMCAT_ONLY{$extension}' in generated web.xml.\n";
            print STDERR "WARN Consider changing it in TOMCAT_ONLY if you want to keep the original definition.\n";
        }
    }
}


# Look for extensions with inconsistent mime types for Tomcat and httpd.
# Log them if they are not in TOMCAT_KEEP
for $extension (@tomcat_extensions) {
    if (exists($httpd{$extension}) && $tomcat{$extension} ne $httpd{$extension}) {
        if (!exists($TOMCAT_KEEP{$extension})) {
            print STDERR "WARN Mapping '$extension' inconsistency is missing from TOMCAT_KEEP.\n";
            print STDERR "WARN Current definition '$extension' -> '$tomcat{$extension}' will be replaced" .
                         " by mime.types definition '$extension' -> '$httpd{$extension}' in generated web.xml.\n";
            print STDERR "WARN Consider adding it to TOMCAT_KEEP if you want to keep the original definition.\n";
        } else {
            print STDERR "INFO Extension '$extension' inconsistency allowed by TOMCAT_KEEP.\n";
            print STDERR "INFO mime.types hat $httpd{$extension}, original web.xml has $tomcat{$extension}\n";
            print STDERR "INFO Consider removing it from TOMCAT_KEEP if you want to use the mime.types definition.\n";
            if ($tomcat{$extension} ne $TOMCAT_KEEP{$extension}) {
                print STDERR "WARN Extension '$extension' is on TOMCAT_KEEP, but has a new definition in web.xml.\n";
                print STDERR "WARN Current definition '$extension' -> '$tomcat{$extension}' will be replaced" .
                             " by '$extension' -> '$TOMCAT_KEEP{$extension}' from TOMCAT_KEEP in generated web.xml.\n";
                print STDERR "WARN Consider changing it in TOMCAT_KEEP if you want to use the definition in the original web.xml.\n";
            }
        }
    }
}


# Log if extensions in web.xml are not sorted alphabetically.
$msg = '';
$previous = '';
for $current (@tomcat_extensions) {
    if ($previous ge $current) {
      $msg .= "WARN Extension '$previous' defined before '$current'\n";
    }
    $previous = $current;
}

if ($msg ne '') {
    print STDERR "WARN MIME type definitions in web.xml were not sorted alphabetically by extension\n";
    print STDERR $msg;
    print STDERR "WARN This will be fixed in the new generated web.xml file '$opt_o'.\n";
}


# Log all extensions defined for httpd but not for Tomcat
for $extension (sort keys %httpd) {
    if (!exists($tomcat{$extension})) {
        print STDERR "INFO Extension '$extension' found for httpd, but not for Tomcat.\n";
        print STDERR "INFO Definition '$extension' -> '$httpd{$extension}' will be added" .
                         " to the generated web.xml.\n";
    }
}


# Generate new web.xml:
#   - Use definitions from httpd
#   - Add TOMCAT_ONLY
#   - Fix TOMCAT_KEEP
#   - output tomcat_pre, sorted mime-mappings, tomcat_post.
while (($extension, $mimetype) = each %TOMCAT_ONLY) {
    $httpd{$extension} = $mimetype;
}
while (($extension, $mimetype) = each %TOMCAT_KEEP) {
    $httpd{$extension} = $mimetype;
}
open ($output_fh, '>', $opt_o) or die "Could not open file '$opt_o' for write - Aborting!";
print $output_fh $tomcat_pre;
for $extension (sort keys %httpd) {
    if (exists($tomcat_commented{$extension})) {
        print $output_fh "    <!--\n";
    }
    print $output_fh "    <mime-mapping>\n";
    if (exists($tomcat_comments{$extension})) {
        print $output_fh "        <!--$tomcat_comments{$extension}-->\n";
    }
    print $output_fh "        <extension>$extension</extension>\n";
    print $output_fh "        <mime-type>$httpd{$extension}</mime-type>\n";
    print $output_fh "    </mime-mapping>\n";
    if (exists($tomcat_commented{$extension})) {
        print $output_fh "    -->\n";
    }
}
print $output_fh $tomcat_post;
close($output_fh);
print "New file '$opt_o' has been written.\n";
