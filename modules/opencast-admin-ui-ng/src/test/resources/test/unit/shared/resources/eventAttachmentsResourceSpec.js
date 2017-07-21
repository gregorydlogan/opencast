describe('Event Attachments API Resource', function () {
    var $httpBackend, EventAttachmentsResource;

    beforeEach(module('adminNg.resources'));
    beforeEach(module('ngResource'));

    beforeEach(inject(function (_$httpBackend_, _EventAttachmentsResource_) {
        $httpBackend  = _$httpBackend_;
        EventAttachmentsResource = _EventAttachmentsResource_;
    }));

    describe('#get', function () {
        beforeEach(function () {
            jasmine.getJSONFixtures().fixturesPath = 'base/app/GET';
            $httpBackend.whenGET('/admin-ng/event/30112/asset/attachment/attachments.json')
            .respond(JSON.stringify(getJSONFixture('admin-ng/event/30112/asset/attachment/attachments.json')));
        });

        it('queries the group API', function () {
            $httpBackend.expectGET('/admin-ng/event/30112/asset/attachment/attachments.json')
            .respond(getJSONFixture('admin-ng/event/30112/asset/attachment/attachments.json'));
            EventAttachmentsResource.get({ id0: '30112'});
            $httpBackend.flush();
        });

        it('returns the parsed JSON', function () {
            $httpBackend.expectGET('/admin-ng/event/30112/asset/attachment/attachments.json')
            .respond(getJSONFixture('admin-ng/event/30112/asset/attachment/attachments.json'));
            var data = EventAttachmentsResource.get({ id0: '30112' });
            $httpBackend.flush();
            expect(data).toBeDefined();
            expect(data.length).toEqual(1);
        });
    });
});
