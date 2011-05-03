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
#
# $Id$
# -----------------------------------------------------------------------------

# The script uses two mime type lists to describe
# the merging between httpd and Tomcat mime types.
#
# 1) %TOMCAT_ONLY: Additional extensions for Tomcat that do not exist in httpd
# 2) %TOMCAT_KEEP: Mime type differences for common extensions where we stick to
#    the Tomcat definition

# The script checks consistency between Tomcat and httpd according
# to the lists 1) and 2) and generates a new web.xml:
#
# A) Additional extensions in Tomcat which are not part of 1)
#    are logged. They will be removed in the generated new web.xml.
#    If you want to keep them, add them to the list 1) and run the
#    script again. If you want to remove them, commit the generated
#    new web.xml.
# B) Mime type differences for the same extension between httpd
#    and Tomcat that are not part of the list 2) are logged.
#    They will be overwritten wit the httpd definition in the generated
#    new web.xml. If you want to keep their Tomcat definition, add them
#    to the list 1) and run the script again. If you want to use the
#    definitions from httpd, commit the generated new web.xml.
# C) Additional extensions in httpd are logged. The script outputs a
#    merged web.xml, which already includes all those additional
#    extensions. If you want to keep them, update web.xml with the
#    generated new web.xml.
# D) If the extensions are not sorted alphabetically, a message is logged.
#    The generated web.xml will be always be sorted alphabetically.
#    If you want to fix the sort order, update web.xml with the generated
#    new web.xml.

use strict;
use locale;
use POSIX qw(locale_h);
use Getopt::Std;

################### BEGIN VARIABLES WHICH MUST BE MAINTAINED #####################

# Script version, printed via getopts with "--version"
$main::VERSION = '1.0';

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
    flac audio/flac
    gz application/x-gzip
    hqx application/mac-binhex40
    htc text/x-component
    jsf text/plain
    jspf text/plain
    m4a audio/mp4
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
    Z application/x-compress
);

# Mime types, that are defined differently
# in Tomcat than in httpd

my %TOMCAT_KEEP = qw(
    cdf application/x-cdf
    class application/java
    exe application/octet-stream
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
my @cols;
my $extension;
my $type;
my $comment;
my $commented;
my $check;
my $msg;


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
our($opt_m, $opt_i, $opt_o);
getopts('m:i:o:');


# Check whether mandatory arguments are given
if ($opt_m eq '' || $opt_i eq '' || $opt_o eq '') {
    HELP_MESSAGE(*STDOUT);
    exit 1;
}


# Switch locale for alphabetical ordering
setlocale(LC_COLLATE, $LOCALE);

# Read and parse httpd mime.types, build up hash extension->mime-type
open(MIMETYPES, "<$opt_m") or die "Could not open file '$opt_m' for read - Aborting!";
while (<MIMETYPES>) {
    chomp($_);
    $line = $_;
    $line =~ s/#.*//;
    $line =~ s/^\s+//;
    if ($line ne '') {
        @cols = split(/\s+/, $line);
        if ($#cols > 0) {
            for ($i=1; $i <= $#cols; $i++) {
                $httpd{$cols[$i]} = $cols[0];
            }
        } else {
            print STDERR "WARN mime.types line ignored: $_\n";
        }
    }
}
close(MIMETYPES);

# Read and parse web.xml, build up hash extension->mime-type
# and store the file parts form before and after mime mappings.
open(WEBXML, "<$opt_i") or die "Could not open file '$opt_i' for read - Aborting!";

# Skip and record all lines before the first mime type definition.
# Because of comment handling we need to read one line ahead.
$line = '';
while (<WEBXML>) {
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
    $_ = <WEBXML>;
    chomp($_);
    $comment = '';
    if ($_ =~ /^\s*<!--([^>]*)-->\s*$/) {
        $comment = $1;
        $_ = <WEBXML>;
        chomp($_);
    }
    if ($_ =~ /^\s*<extension>([^<]*)<\/extension>\s*$/ ) {
        $extension = $1;
        $extension =~ s/^\s+//;
        $extension =~ s/\s+$//;
    } else {
        print STDERR "ERROR Parse error in Tomcat mime-mapping line $.\n";
        print STDERR "ERROR Expected <extension>...</extension>', got '$_' - Aborting!\n";
        close(WEBXML);
        exit 2;
    }
    $_ = <WEBXML>;
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
        close(WEBXML);
        exit 3;
    }
    $_ = <WEBXML>;
    chomp($_);
    if ($_ !~ /^\s*<\/mime-mapping>\s*$/) {
        print STDERR "ERROR Parse error in Tomcat mime-mapping line $.\n";
        print STDERR "ERROR Expected '</mime-mapping>', got '$_' - Aborting!\n";
        close(WEBXML);
        exit 4;
    }
    $_ = <WEBXML>;
    # Check for comment closure
    if ($commented && $_ =~ /^[^<]*-->\s*$/) {
        $commented = 0;
        $_ = <WEBXML>;
    }
    # Check for comment opening
    if ($_ =~ /^\s*<!--[^>]*$/) {
        $commented = 1;
        $line = $_;
        $_ = <WEBXML>;
    }
}

# Add back the last comment line already digested
if ($commented) {
    $tomcat_post = $line;
}

# Read and record the remaining lines
$tomcat_post .= $_;
while (<WEBXML>) {
    if ($_ =~ /<mime-mapping>/) {
        print STDERR "ERROR mime-mapping blocks are not consecutive\n";
        print STDERR "ERROR See line $. in $opt_i - Aborting!\n";
        close(WEBXML);
        exit 5;
    }
    $tomcat_post .= $_;
}

close(WEBXML);


# Look for extensions existing for Tomcat but not for httpd.
# Log them if they are not in TOMCAT_ONLY
for $extension (@tomcat_extensions) {
    if (!exists($httpd{$extension})) {
        if (!exists($TOMCAT_ONLY{$extension})) {
            print STDERR "WARN Extension '$extension' found in web.xml but not in mime.types is missing from TOMCAT_ONLY list.\n";
            print STDERR "WARN Definition '$extension' -> '$tomcat{$extension}' will be removed from generated web.xml.\n";
        } elsif ($tomcat{$extension} ne $TOMCAT_ONLY{$extension}) {
            print STDERR "WARN Additional extension '$extension' allowed by TOMCAT_ONLY list, but has new definition.\n";
            print STDERR "WARN Definition '$extension' -> '$tomcat{$extension}' will be replaced" .
                         " by '$extension' -> '$TOMCAT_ONLY{$extension}' in generated web.xml.\n";
        }
    }
}


# Look for extensions with inconsistent mime types for Tomcat and httpd.
# Log them if they are not in TOMCAT_KEEP
for $extension (@tomcat_extensions) {
    if (exists($httpd{$extension}) && $tomcat{$extension} ne $httpd{$extension}) {
        if (!exists($TOMCAT_KEEP{$extension})) {
            print STDERR "WARN Mapping '$extension' inconsistency is missing from TOMCAT_KEEP list.\n";
            print STDERR "WARN Definition '$extension' -> '$tomcat{$extension}' will be replaced" .
                         " by '$extension' -> '$httpd{$extension}' in generated web.xml.\n";
        } elsif ($tomcat{$extension} ne $TOMCAT_KEEP{$extension}) {
            print STDERR "WARN Extension '$extension' inconsistency allowed by TOMCAT_KEEP list, but has new definition.\n";
            print STDERR "WARN Definition '$extension' -> '$tomcat{$extension}' will be replaced" .
                         " by '$extension' -> '$TOMCAT_KEEP{$extension}' in generated web.xml.\n";
        }
    }
}


# Log if extensions in web.xml are not sorted alphabetically.
$check = 0;
$msg = '';
for ($i=1; $i <= $#tomcat_extensions; $i++) {
    if ($tomcat_extensions[$i - 1] ge $tomcat_extensions[$i]) {
        $check = 1;
        $msg .= "WARN Extension '" . $tomcat_extensions[$i - 1] . "' defined before '" . $tomcat_extensions[$i] . "'\n";
    }
}
if ($check) {
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
for $extension (keys %TOMCAT_ONLY) {
    $httpd{$extension} = $TOMCAT_ONLY{$extension};
}
for $extension (keys %TOMCAT_KEEP) {
    $httpd{$extension} = $TOMCAT_KEEP{$extension};
}
open (NEW, ">$opt_o") or die "Could not open file '$opt_o' for write - Aborting!";
print NEW $tomcat_pre;
for $extension (sort keys %httpd) {
    if (exists($tomcat_commented{$extension})) {
        print NEW "    <!--\n";
    }
    print NEW "    <mime-mapping>\n";
    if (exists($tomcat_comments{$extension})) {
        print NEW "        <!--$tomcat_comments{$extension}-->\n";
    }
    print NEW "        <extension>$extension</extension>\n";
    print NEW "        <mime-type>$httpd{$extension}</mime-type>\n";
    print NEW "    </mime-mapping>\n";
    if (exists($tomcat_commented{$extension})) {
        print NEW "    -->\n";
    }
}
print NEW $tomcat_post;
close(NEW);
print "New file '$opt_o' has been written.\n";

