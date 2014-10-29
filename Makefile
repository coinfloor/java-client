LIBDIR := ..
OUTDIR := out

JAVAC_OPTS := -Xlint:all $(JAVAC_OPTS)

NAME := coinfloor-client
PACKAGES := uk
MAINCLASS := uk.co.coinfloor.client.SwingClient
LIBRARIES := $(strip \
	$(wildcard $(LIBDIR)/coinfloor-library-*.jar) $(wildcard $(LIBDIR)/coinfloor-library.jar) \
	)

EMPTY :=
SPACE := $(EMPTY) $(EMPTY)
CLASSPATH := $(subst $(SPACE),:,$(LIBRARIES))

COMMIT := $(shell git describe --always --dirty)
ifeq ($(COMMIT),)
JARFILE := $(OUTDIR)/$(NAME).jar
else
JARFILE := $(OUTDIR)/$(NAME)-g$(COMMIT).jar
endif

.PHONY : default all tests clean

default : all

all : $(JARFILE)

clean :
	rm -rf '$(OUTDIR)'

$(OUTDIR) :
	mkdir -p '$(OUTDIR)'

$(JARFILE) : $(OUTDIR) $(shell find $(PACKAGES))
	rm -rf $(addprefix '$(OUTDIR)'/,$(PACKAGES))
	find $(PACKAGES) -name '*.java' -print0 | xargs -0 -r javac $(JAVAC_OPTS) -d '$(OUTDIR)' -cp '$(CLASSPATH)'
	echo 'Class-Path: $(subst $(LIBDIR)/,,$(LIBRARIES))' > '$(OUTDIR)/Manifest'
	jar -cfme '$(JARFILE)' '$(OUTDIR)/Manifest' '$(MAINCLASS)' -C '$(OUTDIR)' $(PACKAGES)
